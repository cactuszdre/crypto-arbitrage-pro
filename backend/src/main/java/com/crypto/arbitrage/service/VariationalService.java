package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for fetching funding rates from Variational using the Read-Only API.
 * 
 * Variational is a decentralized permissionless trading protocol for
 * generalized derivatives.
 * 
 * READ-ONLY API (January 2026):
 * - Base URL: https://omni-client-api.prod.ap-northeast-1.variational.io
 * - Endpoint: GET /metadata/stats
 * - Rate Limits: 10 requests per 10 seconds (per IP), 1000 requests per minute
 * (global)
 * - No authentication required
 * 
 * The funding rate is returned as a decimal (e.g., 0.109500 = 10.95% annual
 * rate).
 * Funding intervals vary per market (1h, 2h, 4h, 8h) as indicated by
 * funding_interval_s.
 * 
 * @see <a href=
 *      "https://docs.variational.io/technical-documentation/api">Variational
 *      API Docs</a>
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

    private static final String DEX_NAME = "Variational";
    private static final String BASE_URL = "https://omni-client-api.prod.ap-northeast-1.variational.io";
    private static final String STATS_ENDPOINT = "/metadata/stats";

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    /**
     * Fetches funding data from Variational's Read-Only API.
     * 
     * The /metadata/stats endpoint returns platform-wide and per-listing market
     * statistics.
     * 
     * Rate Limits:
     * - Per IP: 10 requests per 10 seconds
     * - Global: 1000 requests per minute
     */
    @Override
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.debug("DEX Variational not found in database");
            return List.of();
        }

        Dex dex = dexOpt.get();

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(BASE_URL)
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024)) // 16MB for large response
                    .build();

            JsonNode response = webClient.get()
                    .uri(STATS_ENDPOINT)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Empty response from Variational API");
                return List.of();
            }

            List<FundingRate> rates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            // API structure: response has a "listings" array
            JsonNode listingsArray = response.get("listings");
            if (listingsArray == null || !listingsArray.isArray()) {
                log.warn("No 'listings' array found in Variational API response");
                return List.of();
            }

            // Process each listing
            for (JsonNode listing : listingsArray) {
                FundingRate rate = parseListingFundingRate(listing, dex, now);
                if (rate != null) {
                    rates.add(rate);
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
     * Parse a single listing's funding rate from the Read-Only API response.
     * 
     * Response structure per listing:
     * {
     * "ticker": "BTC",
     * "name": "Bitcoin",
     * "mark_price": "93787.9606019699",
     * "volume_24h": "1058107020.462713",
     * "funding_rate": "0.109500", // ANNUALIZED rate as decimal (10.95% APR)
     * "funding_interval_s": 28800, // Interval in seconds (8h = 28800)
     * ...
     * }
     * 
     * NOTE: funding_rate is ANNUALIZED as a decimal.
     * According to docs: "Funding rates are decimals (multiply by 100 for
     * percentage)"
     */
    private FundingRate parseListingFundingRate(JsonNode listing, Dex dex, LocalDateTime timestamp) {
        try {
            // Get ticker symbol (required field)
            if (!listing.has("ticker")) {
                return null;
            }
            String tickerSymbol = listing.get("ticker").asText();

            // Check for funding rate
            if (!listing.has("funding_rate")) {
                return null;
            }

            String fundingRateStr = listing.get("funding_rate").asText();
            if (fundingRateStr == null || fundingRateStr.isEmpty() ||
                    "null".equalsIgnoreCase(fundingRateStr) || "nan".equalsIgnoreCase(fundingRateStr)) {
                return null;
            }

            // funding_rate from Variational is ANNUALIZED as a decimal
            // e.g., 0.109500 = 10.95% APR, -0.213670 = -21.37% APR
            BigDecimal annualRateDecimal = new BigDecimal(fundingRateStr);

            // Get funding interval in seconds (default to 8 hours)
            // Note: Variational has 1h, 2h, 4h, and 8h intervals depending on the asset
            int fundingIntervalSeconds = listing.has("funding_interval_s")
                    ? listing.get("funding_interval_s").asInt()
                    : 28800;

            // Convert to hours
            int intervalHours = fundingIntervalSeconds / 3600;
            if (intervalHours < 1)
                intervalHours = 1;

            // Normalize symbol to standard format (e.g., BTC -> BTC-USD-PERP)
            String symbol = normalizeSymbol(tickerSymbol);

            // Get or create ticker
            Ticker ticker = tickerService.getOrCreate(symbol);

            // Calculate hourly rate from annual rate
            // hourly_rate = annual_rate / (24 * 365)
            BigDecimal hourlyRate = annualRateDecimal.divide(
                    BigDecimal.valueOf(24 * 365), 18, RoundingMode.HALF_UP);

            // Rate 8h % = annualRateDecimal * (8 / (24 * 365)) * 100
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
            String tickerName = listing.has("ticker") ? listing.get("ticker").asText() : "unknown";
            log.debug("Error parsing funding rate for {}: {}", tickerName, e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Variational symbol format to the standard format used in this
     * application.
     * Variational uses: BTC, ETH, SOL, etc.
     * Our format: BTC-USD-PERP (standard across other DEXes)
     */
    private String normalizeSymbol(String symbol) {
        if (symbol.endsWith("-USD-PERP")) {
            return symbol;
        }
        return symbol + "-USD-PERP";
    }

    /**
     * Fetches available trading symbols from Variational.
     */
    public List<String> fetchAvailableSymbols() {
        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(BASE_URL)
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri(STATS_ENDPOINT)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                return List.of();
            }

            JsonNode listingsArray = response.get("listings");
            if (listingsArray == null || !listingsArray.isArray()) {
                return List.of();
            }

            List<String> symbols = new ArrayList<>();
            for (JsonNode listing : listingsArray) {
                if (listing.has("ticker")) {
                    String ticker = listing.get("ticker").asText();
                    symbols.add(normalizeSymbol(ticker));
                }
            }

            return symbols;

        } catch (Exception e) {
            log.error("Error fetching symbols from Variational: {}", e.getMessage());
            return List.of();
        }
    }
}
