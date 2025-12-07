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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParadexService {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final WebClient.Builder webClientBuilder;
    private final EncryptionService encryptionService;

    private static final String DEX_NAME = "Paradex";
    
    // Cache for market info (symbol -> funding_period_hours)
    private final Map<String, Integer> marketFundingPeriodCache = new ConcurrentHashMap<>();

    /**
     * Loads market info from Paradex API and caches funding_period_hours for each symbol.
     * This is needed because different pairs have different funding periods (e.g., BTC=8h, MOODENG=4h).
     */
    public void loadMarketInfo() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return;
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
                return;
            }

            for (JsonNode market : response.get("results")) {
                String assetKind = market.has("asset_kind") ? market.get("asset_kind").asText() : "";
                if ("PERP".equals(assetKind) && market.has("symbol") && market.has("funding_period_hours")) {
                    String symbol = market.get("symbol").asText();
                    int fundingPeriodHours = market.get("funding_period_hours").asInt(8); // default to 8 if missing
                    marketFundingPeriodCache.put(symbol, fundingPeriodHours);
                }
            }

            log.info("Loaded market info for {} Paradex symbols", marketFundingPeriodCache.size());

        } catch (Exception e) {
            log.error("Error loading Paradex market info: {}", e.getMessage());
        }
    }

    /**
     * Gets the funding period hours for a symbol, loading market info if not cached.
     */
    private int getFundingPeriodHours(String symbol, String baseUrl) {
        if (marketFundingPeriodCache.isEmpty()) {
            loadMarketInfo();
        }
        return marketFundingPeriodCache.getOrDefault(symbol, 8); // default to 8h
    }

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

        // Get the funding period for this specific symbol
        int fundingPeriodHours = getFundingPeriodHours(symbol, baseUrl);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/funding/data")
                            .queryParam("market", symbol)
                            .build())
                    .header("Authorization", "Bearer " + decryptedApiKey)
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
                FundingRate rate = parseFundingRate(node, dex, symbol, fundingPeriodHours);
                if (rate != null) {
                    rates.add(rate);
                }
            }

            log.info("Récupéré {} funding rates pour {} sur Paradex (period={}h)", rates.size(), symbol, fundingPeriodHours);
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

    private FundingRate parseFundingRate(JsonNode node, Dex dex, String symbol, int fundingPeriodHours) {
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

            // Log raw value for debugging
            log.debug("Paradex {} raw funding_rate: {}, period: {}h", symbol, rate, fundingPeriodHours);

            // The funding_rate from Paradex API is the rate for the funding period.
            // Different pairs have different funding periods:
            // - BTC/ETH: 8h (3 periods per day)
            // - MOODENG/ASTER etc: 4h (6 periods per day)
            // 
            // Rate for period % = rate * 100
            BigDecimal ratePeriod = rate.multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);
            
            // Calculate periods per day based on the actual funding period
            int periodsPerDay = 24 / fundingPeriodHours;
            
            // Annualized % = rate * periodsPerDay * 365 * 100
            BigDecimal rateAnnual = rate.multiply(BigDecimal.valueOf(periodsPerDay * 365))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            
            log.debug("Paradex {} calculated: ratePeriod={}%, rateAnnual={}% (periodsPerDay={})", 
                    symbol, ratePeriod, rateAnnual, periodsPerDay);

            return FundingRate.builder()
                    .dex(dex)
                    .symbol(symbol)
                    .rate(rate)
                    .rate8h(ratePeriod) // Note: This is actually the period rate, not strictly 8h for all pairs
                    .rateAnnual(rateAnnual)
                    .fundingPremium(fundingPremium)
                    .fundingIndex(fundingIndex)
                    .intervalHours(fundingPeriodHours) // Use the actual funding period, not the dex default
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate: {}", e.getMessage());
            return null;
        }
    }
}
