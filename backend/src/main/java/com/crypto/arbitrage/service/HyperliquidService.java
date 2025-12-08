package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
public class HyperliquidService {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String DEX_NAME = "Hyperliquid";
    private static final String DEFAULT_BASE_URL = "https://api.hyperliquid.xyz";

    /**
     * Fetches funding data from Hyperliquid API using the metaAndAssetCtxs endpoint.
     * This provides current funding rates for all perpetual assets.
     */
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX Hyperliquid non trouvé en base de données");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() 
                ? dex.getBaseUrl() 
                : DEFAULT_BASE_URL;

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(baseUrl).build();

            // POST request with JSON body { "type": "metaAndAssetCtxs" }
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("type", "metaAndAssetCtxs");

            JsonNode response = webClient.post()
                    .uri("/info")
                    .header("Content-Type", "application/json")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.isArray() || response.size() < 2) {
                log.warn("Réponse invalide de Hyperliquid: {}", response);
                return List.of();
            }

            // Response structure: [meta, assetCtxs]
            // meta = { "universe": [...list of assets...] }
            // assetCtxs = [...array of contexts with funding rates...]
            JsonNode metaNode = response.get(0);
            JsonNode assetCtxsNode = response.get(1);

            if (metaNode == null || !metaNode.has("universe") || !assetCtxsNode.isArray()) {
                log.warn("Format de réponse Hyperliquid inattendu");
                return List.of();
            }

            JsonNode universeNode = metaNode.get("universe");
            if (!universeNode.isArray()) {
                log.warn("Universe node n'est pas un tableau");
                return List.of();
            }

            Map<String, FundingRate> uniqueRates = new HashMap<>();
            LocalDateTime now = LocalDateTime.now();

            // Parse each asset context (index matches universe array)
            for (int i = 0; i < universeNode.size() && i < assetCtxsNode.size(); i++) {
                JsonNode assetMeta = universeNode.get(i);
                JsonNode assetCtx = assetCtxsNode.get(i);

                FundingRate rate = parseFundingRate(assetMeta, assetCtx, dex, now);
                if (rate != null) {
                    uniqueRates.put(rate.getSymbol(), rate);
                }
            }

            List<FundingRate> rates = new ArrayList<>(uniqueRates.values());

            // Save all fetched rates
            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                log.info("Récupéré et sauvegardé {} funding rates pour Hyperliquid", rates.size());
            }

            return rates;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des funding data Hyperliquid: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Fetches available trading symbols from Hyperliquid.
     */
    public List<String> fetchAvailableSymbols() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() 
                ? dex.getBaseUrl() 
                : DEFAULT_BASE_URL;

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(baseUrl).build();

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("type", "meta");

            JsonNode response = webClient.post()
                    .uri("/info")
                    .header("Content-Type", "application/json")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("universe") || !response.get("universe").isArray()) {
                return List.of();
            }

            List<String> symbols = new ArrayList<>();
            for (JsonNode asset : response.get("universe")) {
                if (asset.has("name")) {
                    String coin = asset.get("name").asText();
                    symbols.add(coin + "-USD-PERP");
                }
            }

            return symbols;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des symboles Hyperliquid: {}", e.getMessage());
            return List.of();
        }
    }

    private FundingRate parseFundingRate(JsonNode assetMeta, JsonNode assetCtx, Dex dex, LocalDateTime timestamp) {
        try {
            if (assetMeta == null || assetCtx == null) {
                return null;
            }

            // assetMeta has: { "name": "BTC", "szDecimals": 4, ... }
            // assetCtx has: { "funding": "0.00012345", "openInterest": "...", "prevDayPx": "...", ... }
            
            if (!assetMeta.has("name") || !assetCtx.has("funding")) {
                return null;
            }

            String coin = assetMeta.get("name").asText();
            String fundingStr = assetCtx.get("funding").asText();

            // Validate that fundingStr is a number (handles "nan", "null" strings etc)
            // Sometimes APIs return "nan" for new listings
            if (fundingStr == null || fundingStr.isEmpty() || "nan".equalsIgnoreCase(fundingStr) || "null".equalsIgnoreCase(fundingStr)) {
                return null;
            }

            // Normalize symbol to standard format (e.g. BTC-USD-PERP)
            String symbol = coin + "-USD-PERP";
            
            // Get or create ticker
            Ticker ticker = tickerService.getOrCreate(symbol);

            // Parse funding rate
            // Hyperliquid returns hourly funding rate in decimal format
            BigDecimal rate = new BigDecimal(fundingStr);

            // Hyperliquid funding is hourly, so:
            // Rate 8h % = rate * 8 * 100
            BigDecimal rate8h = rate.multiply(BigDecimal.valueOf(8))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);

            // Annual Rate % = rate * 24 * 365 * 100
            BigDecimal rateAnnual = rate.multiply(BigDecimal.valueOf(24 * 365))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Get premium if available and valid
            BigDecimal premium = null;
            if (assetCtx.has("premium")) {
                String premiumStr = assetCtx.get("premium").asText();
                if (premiumStr != null && !premiumStr.isEmpty() 
                        && !"nan".equalsIgnoreCase(premiumStr) 
                        && !"null".equalsIgnoreCase(premiumStr)) {
                    premium = new BigDecimal(premiumStr);
                }
            }

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(rate)
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .fundingPremium(premium)
                    .intervalHours(1) // Hyperliquid has hourly funding
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate Hyperliquid: {}", e.getMessage());
            return null;
        }
    }
}
