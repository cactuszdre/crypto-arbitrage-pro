package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParadexService {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final WebClient.Builder webClientBuilder;
    private final EncryptionService encryptionService;

    private static final String DEX_NAME = "Paradex";

    /**
     * Récupère les données de funding depuis l'API Paradex pour un symbole donné
     */
    public List<FundingRate> fetchFundingData(String symbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX Paradex non trouvé en base de données");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl();
        String apiKey = dex.getApiKey();
        String decryptedApiKey = encryptionService.decrypt(apiKey);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            // Note: For Paradex read-only public endpoints, API Key might not be strictly required in headers
            // but we demonstrate usage here. If it were required:
            // .header("Authorization", "Bearer " + decryptedApiKey)

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/funding/data")
                            .queryParam("market", symbol)
                            .build())
                    .header("Authorization", "Bearer " + decryptedApiKey) // Adding bearer token as example of usage
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("results")) {
                log.warn("Réponse vide ou invalide de Paradex pour {}", symbol);
                return List.of();
            }

            List<FundingRate> rates = new ArrayList<>();
            JsonNode results = response.get("results");

            for (JsonNode node : results) {
                FundingRate rate = parseFundingRate(node, dex, symbol);
                if (rate != null) {
                    rates.add(rate);
                }
            }

            log.info("Récupéré {} funding rates pour {} sur Paradex", rates.size(), symbol);
            return rates;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des funding data Paradex: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Récupère le dernier funding rate et le sauvegarde en base
     */
    public Optional<FundingRate> fetchAndSaveLatestFundingRate(String symbol) {
        List<FundingRate> rates = fetchFundingData(symbol);
        if (rates.isEmpty()) {
            return Optional.empty();
        }

        // Le premier élément est le plus récent
        FundingRate latestRate = rates.get(0);
        
        // Sauvegarde en base
        FundingRate saved = fundingRateRepository.save(latestRate);
        log.info("Sauvegardé funding rate pour {} : rate={}", symbol, saved.getRate());
        
        return Optional.of(saved);
    }

    /**
     * Récupère les symboles disponibles sur Paradex (marchés PERP)
     */
    public List<String> fetchAvailableSymbols() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl();

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            JsonNode response = webClient.get()
                    .uri("/markets")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("results")) {
                return List.of();
            }

            List<String> symbols = new ArrayList<>();
            for (JsonNode market : response.get("results")) {
                String assetKind = market.has("asset_kind") ? market.get("asset_kind").asText() : "";
                if ("PERP".equals(assetKind)) {
                    symbols.add(market.get("symbol").asText());
                }
            }

            log.info("Trouvé {} symboles PERP sur Paradex", symbols.size());
            return symbols;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des symboles Paradex: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Récupère le BBO (Best Bid/Offer) pour un symbole
     */
    public Optional<JsonNode> fetchBBO(String symbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return Optional.empty();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl();

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            JsonNode response = webClient.get()
                    .uri("/bbo/" + symbol)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return Optional.ofNullable(response);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération du BBO Paradex: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private FundingRate parseFundingRate(JsonNode node, Dex dex, String symbol) {
        try {
            BigDecimal rate = new BigDecimal(node.get("funding_rate").asText());
            BigDecimal fundingPremium = node.has("funding_premium") 
                    ? new BigDecimal(node.get("funding_premium").asText()) 
                    : null;
            BigDecimal fundingIndex = node.has("funding_index") 
                    ? new BigDecimal(node.get("funding_index").asText()) 
                    : null;

            long timestampMs = node.get("created_at").asLong();
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMs), 
                    ZoneId.systemDefault()
            );

            // Calcul des taux (rate est en pourcentage instantané)
            // Pour 8h: rate * 8 * 100 (car le funding est payé toutes les 8h sur Paradex)
            BigDecimal rate8h = rate.multiply(BigDecimal.valueOf(8))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);
            
            // Annualisé: rate * 8 * 3 * 365 * 100
            BigDecimal rateAnnual = rate.multiply(BigDecimal.valueOf(8 * 3 * 365))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            return FundingRate.builder()
                    .dex(dex)
                    .symbol(symbol)
                    .rate(rate)
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .fundingPremium(fundingPremium)
                    .fundingIndex(fundingIndex)
                    .intervalHours(dex.getFundingIntervalHours())
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate: {}", e.getMessage());
            return null;
        }
    }
}
