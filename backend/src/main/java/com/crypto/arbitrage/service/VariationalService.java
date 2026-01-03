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
import java.util.*;

/**
 * Service for fetching funding rates from Variational (omni.variational.io)
 * 
 * Variational is a decentralized permissionless trading protocol for generalized derivatives.
 * The funding rate is returned as a percentage (e.g., 0.1095 = 0.1095% per interval).
 * Funding intervals vary per market (1h, 4h, 8h) as indicated by funding_interval_s.
 * 
 * API Discovery:
 * - Instruments endpoint returns all markets with their current funding rates
 * - No authentication required for public market data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VariationalService implements DexFetcher {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String DEX_NAME = "Variational";
    private static final String DEFAULT_BASE_URL = "https://omni.variational.io";
    private static final String SUPPORTED_ASSETS_ENDPOINT = "/api/metadata/supported_assets";

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    /**
     * Fetches funding data from Variational's instruments API.
     * The API returns all markets with their current funding rates included.
     */
    @Override
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.debug("DEX Variational not found in database");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty()
                ? dex.getBaseUrl()
                : DEFAULT_BASE_URL;

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .defaultHeader("Content-Type", "application/json")
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024)) // 16MB for large response
                    .build();

            // Fetch all instruments with funding rates
            JsonNode response = webClient.get()
                    .uri(SUPPORTED_ASSETS_ENDPOINT)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Empty response from Variational instruments API");
                return List.of();
            }

            List<FundingRate> rates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            // Response is an object where keys are asset symbols
            // Each value is an array of market info objects
            Iterator<Map.Entry<String, JsonNode>> fields = response.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String assetSymbol = entry.getKey();
                JsonNode marketsArray = entry.getValue();

                if (marketsArray.isArray()) {
                    for (JsonNode market : marketsArray) {
                        FundingRate rate = parseMarketFundingRate(assetSymbol, market, dex, now);
                        if (rate != null) {
                            rates.add(rate);
                        }
                    }
                }
            }

            // Save all fetched rates
            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                lastFundingService.upsertAll(rates);
            }

            return rates;

        } catch (Exception e) {
            log.error("Error fetching funding data from Variational: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Parse a single market's funding rate from the supported_assets response.
     * 
     * Response structure per market:
     * {
     *   "asset": "BTC",
     *   "funding_rate": "0.102288",          // ANNUALIZED rate as decimal (10.23% APR)
     *   "funding_interval_s": 28800,          // Interval in seconds (8h = 28800)
     *   "has_perp": true,
     *   ...
     * }
     * 
     * NOTE: funding_rate from Variational is already ANNUALIZED (APR).
     * A value of 0.102288 means 10.23% annual rate.
     */
    private FundingRate parseMarketFundingRate(String assetSymbol, JsonNode market, Dex dex, LocalDateTime timestamp) {
        try {
            // Only process markets with perpetual futures
            if (!market.has("has_perp") || !market.get("has_perp").asBoolean()) {
                return null;
            }

            // Check for funding rate
            if (!market.has("funding_rate")) {
                return null;
            }

            String fundingRateStr = market.get("funding_rate").asText();
            if (fundingRateStr == null || fundingRateStr.isEmpty() || 
                "null".equalsIgnoreCase(fundingRateStr) || "nan".equalsIgnoreCase(fundingRateStr)) {
                return null;
            }

            // funding_rate from Variational is ANNUALIZED as a decimal
            // e.g., 0.102288 = 10.23% APR
            BigDecimal annualRateDecimal = new BigDecimal(fundingRateStr);

            // Get funding interval in seconds (default to 8 hours)
            int fundingIntervalSeconds = market.has("funding_interval_s") 
                    ? market.get("funding_interval_s").asInt() 
                    : 28800;
            
            // Convert to hours
            int intervalHours = fundingIntervalSeconds / 3600;
            if (intervalHours < 1) intervalHours = 1;

            // Normalize symbol to standard format (e.g., BTC -> BTC-USD-PERP)
            String symbol = normalizeSymbol(assetSymbol);
            
            // Get or create ticker
            Ticker ticker = tickerService.getOrCreate(symbol);

            // Calculate hourly rate from annual rate
            // hourly_rate = annual_rate / (24 * 365)
            BigDecimal hourlyRate = annualRateDecimal.divide(
                    BigDecimal.valueOf(24 * 365), 18, RoundingMode.HALF_UP);

            // Rate 8h % = annualRateDecimal * (8 / (24 * 365)) * 100
            // Simplified: = annualRateDecimal / 1095 * 100 = annualRateDecimal * 100 / 1095
            BigDecimal rate8h = annualRateDecimal
                    .multiply(BigDecimal.valueOf(100))
                    .multiply(BigDecimal.valueOf(8))
                    .divide(BigDecimal.valueOf(24 * 365), 6, RoundingMode.HALF_UP);

            // Annual Rate % = annualRateDecimal * 100
            BigDecimal rateAnnual = annualRateDecimal
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(hourlyRate) // Store hourly rate for consistency with other DEXes
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .intervalHours(intervalHours)
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing funding rate for asset {}: {}", assetSymbol, e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Variational symbol format to the standard format used in this application.
     * Variational uses: BTC, ETH, SOL, etc.
     * Our format: BTC-USD-PERP (standard across other DEXes)
     */
    private String normalizeSymbol(String symbol) {
        // Already in standard format
        if (symbol.endsWith("-USD-PERP")) {
            return symbol;
        }
        // Add USD-PERP suffix
        return symbol + "-USD-PERP";
    }

    /**
     * Fetches available trading symbols from Variational.
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
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri(SUPPORTED_ASSETS_ENDPOINT)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                return List.of();
            }

            List<String> symbols = new ArrayList<>();
            Iterator<String> fieldNames = response.fieldNames();
            
            while (fieldNames.hasNext()) {
                String assetSymbol = fieldNames.next();
                JsonNode marketsArray = response.get(assetSymbol);
                
                if (marketsArray.isArray()) {
                    for (JsonNode market : marketsArray) {
                        if (market.has("has_perp") && market.get("has_perp").asBoolean()) {
                            symbols.add(normalizeSymbol(assetSymbol));
                            break; // Only add once per asset
                        }
                    }
                }
            }

            return symbols;

        } catch (Exception e) {
            log.error("Error fetching symbols from Variational: {}", e.getMessage());
            return List.of();
        }
    }
}
