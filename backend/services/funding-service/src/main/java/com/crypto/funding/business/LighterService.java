package com.crypto.funding.business;

import com.crypto.funding.model.Dex;
import com.crypto.funding.model.FundingRate;
import com.crypto.funding.model.Ticker;
import com.crypto.funding.repository.DexRepository;
import com.crypto.funding.repository.FundingRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LighterService implements DexFetcher {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;

    private static final String DEX_NAME = "Lighter";
    private static final String DEFAULT_BASE_URL = "https://mainnet.zklighter.elliot.ai/api/v1";

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override

    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX Lighter non trouvé en base de données");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() 
                ? dex.getBaseUrl() 
                : DEFAULT_BASE_URL;

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(baseUrl).build();

            // Endpoint: /funding-rates
            JsonNode response = webClient.get()
                    .uri("/funding-rates")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Réponse vide de Lighter");
                return List.of();
            }

            JsonNode ratesNode = response;
            // Handle if response is wrapped in an object (e.g. { "funding_rates": [...] })
            if (response.isObject()) {
                if (response.has("funding_rates")) {
                    ratesNode = response.get("funding_rates");
                } else if (response.has("rates")) {
                    ratesNode = response.get("rates");
                } else if (response.has("result")) { // Some APIs use "result"
                     ratesNode = response.get("result");
                }
            }
            
            if (!ratesNode.isArray()) {
                 log.warn("Format de réponse invalide pour Lighter (attendu tableau): {}", response.toPrettyString());
                 return List.of();
            }

            Map<String, FundingRate> uniqueRates = new HashMap<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode node : ratesNode) {
                FundingRate rate = parseFundingRate(node, dex, now);
                if (rate != null) {
                    // This will overwrite if duplicates exist, ensuring only one per symbol is saved
                    uniqueRates.put(rate.getSymbol(), rate);
                }
            }
            
            List<FundingRate> rates = new ArrayList<>(uniqueRates.values());
            
            // Save all fetched rates
            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                // Update last_funding table
                lastFundingService.upsertAll(rates);
            }

            return rates;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des funding data Lighter: {}", e.getMessage());
            return List.of();
        }
    }

    private FundingRate parseFundingRate(JsonNode node, Dex dex, LocalDateTime timestamp) {
        try {
            // Lighter API returns: { "market_id": 1, "symbol": "BTC_USDC", "rate": 0.0001, "exchange": "lighter" }
            if (node.has("exchange")) {
                String exchange = node.get("exchange").asText();
                if (!"lighter".equalsIgnoreCase(exchange)) {
                    return null; // Skip rates from other exchanges (e.g. binance)
                }
            }

            String rawSymbol = node.get("symbol").asText();
            
            // Extract base symbol (e.g. from "BTC_USDC" -> "BTC", or just "BTC")
            String baseSymbol = rawSymbol.split("_")[0];
            
            // Removed strict filter `ALLOWED_TOKENS` to allow all tokens

            String symbol = baseSymbol + "-USD-PERP"; // Normalize to standardized format (e.g. BTC-USD-PERP)
            BigDecimal rate = new BigDecimal(node.get("rate").asText());
            
            // API returns the rate in decimal format corresponding to the displayed 8h Funding Rate.
            // Example: API -0.001248 corresponds to -0.1248% (which is the 8h rate).
            // The rate is quoted as 8h equivalent, but payments are made hourly.
            
            // Calculate hourly rate from 8h rate
            // rateHourly = rate / 8
            BigDecimal rateHourly = rate.divide(BigDecimal.valueOf(8), 12, RoundingMode.HALF_UP);
            
            // Rate 8h % = rateHourly * 8 * 100 (standardized, same as Hyperliquid)
            BigDecimal rate8h = rateHourly.multiply(BigDecimal.valueOf(8))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);
            
            // Annual Rate % = rateHourly * 24 * 365 * 100
            BigDecimal rateAnnual = rateHourly.multiply(BigDecimal.valueOf(24 * 365))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Get or create ticker
            Ticker ticker = tickerService.getOrCreate(symbol);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(rateHourly) // Store the hourly rate for consistency
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .intervalHours(1) // Payments are hourly
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate Lighter: {}", e.getMessage());
            return null;
        }
    }
}
