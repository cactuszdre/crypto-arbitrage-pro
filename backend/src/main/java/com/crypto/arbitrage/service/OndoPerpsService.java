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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for fetching funding rates from Ondo Perps (ondoperps.xyz).
 * <p>
 * Public REST API (no auth):
 * - GET /v1/markets
 * - GET /v1/perps/funding_rates?market=AAPL-USD.P
 * <p>
 * Funding interval: 3h (8 intervals/day). Rate from API is per 3h interval.
 * Similar to Paradex: fetch is per-market (one market per request).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OndoPerpsService {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;

    private static final String DEX_NAME = "Ondo Perps";
    /** Ondo settles funding every hour (history timestamps are hourly). */
    private static final int FUNDING_INTERVAL_HOURS = 1;
    private static final int PERIODS_PER_DAY = 24;

    /**
     * Fetches the current funding rate for a normalized symbol and saves it.
     *
     * @param normalizedSymbol e.g. "BTC-USD-PERP" or "AAPL-USD-PERP"
     */
    public Optional<FundingRate> fetchAndSaveLatestFundingRate(String normalizedSymbol) {
        Optional<FundingRate> rate = fetchFundingData(normalizedSymbol);
        if (rate.isEmpty()) {
            return Optional.empty();
        }

        FundingRate saved = fundingRateRepository.save(rate.get());
        lastFundingService.upsertFromFundingRate(saved);
        return Optional.of(saved);
    }

    /**
     * Fetches the current funding rate for a normalized symbol (does not save).
     */
    public Optional<FundingRate> fetchFundingData(String normalizedSymbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX {} not found in database", DEX_NAME);
            return Optional.empty();
        }

        Dex dex = dexOpt.get();
        String market = toApiMarket(normalizedSymbol);

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(dex.getBaseUrl()).build();

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/perps/funding_rates")
                            .queryParam("market", market)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                log.warn("Empty or unsuccessful response from Ondo Perps for {}", market);
                return Optional.empty();
            }

            JsonNode result = response.get("result");
            if (result == null || !result.has("rate")) {
                log.warn("Invalid funding rate payload from Ondo Perps for {}", market);
                return Optional.empty();
            }

            FundingRate rate = parseFundingRate(result, dex, normalizedSymbol);
            return Optional.ofNullable(rate);

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("WebClient error from Ondo Perps for {}: {} - {}", market, e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.warn("Timeout fetching Ondo Perps funding for {}", market);
                return Optional.empty();
            }
            log.error("Error fetching Ondo Perps funding for {}: {}", market, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches available (non-disabled) perpetual markets, normalized to *-USD-PERP.
     */
    public List<String> fetchAvailableSymbols() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }

        Dex dex = dexOpt.get();

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(dex.getBaseUrl())
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri("/v1/markets")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                return List.of();
            }

            JsonNode tradingPairs = response.path("result").path("perps").path("tradingPairs");
            if (!tradingPairs.isArray()) {
                return List.of();
            }

            List<String> symbols = new ArrayList<>();
            for (JsonNode pair : tradingPairs) {
                if (pair.path("disabled").asBoolean(false)) {
                    continue;
                }
                String market = pair.path("market").asText(null);
                if (market == null || market.isBlank()) {
                    continue;
                }
                symbols.add(normalizeSymbol(market));
            }

            return symbols;

        } catch (Exception e) {
            log.error("Error fetching Ondo Perps markets: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Ondo API: BTC-USD.P → normalized: BTC-USD-PERP
     */
    static String normalizeSymbol(String apiMarket) {
        if (apiMarket == null) {
            return null;
        }
        if (apiMarket.endsWith("-USD.P")) {
            return apiMarket.replace("-USD.P", "-USD-PERP");
        }
        if (apiMarket.endsWith(".P")) {
            return apiMarket.substring(0, apiMarket.length() - 2) + "-PERP";
        }
        return apiMarket;
    }

    /**
     * Normalized: BTC-USD-PERP → Ondo API: BTC-USD.P
     */
    static String toApiMarket(String normalizedSymbol) {
        if (normalizedSymbol == null) {
            return null;
        }
        if (normalizedSymbol.endsWith("-USD-PERP")) {
            return normalizedSymbol.replace("-USD-PERP", "-USD.P");
        }
        if (normalizedSymbol.endsWith("-PERP")) {
            return normalizedSymbol.substring(0, normalizedSymbol.length() - 5) + ".P";
        }
        return normalizedSymbol;
    }

    private FundingRate parseFundingRate(JsonNode result, Dex dex, String normalizedSymbol) {
        try {
            // API `rate` is the hourly funding rate as a decimal
            // (matches UI: 0.0000063 → 0.00063%/h → 5.5188% annual).
            // History confirms settlements every hour, not every 3h.
            BigDecimal rateHourly = new BigDecimal(result.get("rate").asText());

            // Rate 8h % = hourly * 8 * 100
            BigDecimal rate8h = rateHourly
                    .multiply(BigDecimal.valueOf(8))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);

            // Annualized % = hourly * 24 * 365 * 100
            BigDecimal rateAnnual = rateHourly
                    .multiply(BigDecimal.valueOf(PERIODS_PER_DAY * 365L))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal fundingPremium = extractLatestPremium(result.get("premiums"));

            LocalDateTime timestamp = LocalDateTime.now();
            if (result.has("intervalEnds") && !result.get("intervalEnds").isNull()) {
                try {
                    Instant ends = Instant.parse(result.get("intervalEnds").asText());
                    timestamp = LocalDateTime.ofInstant(
                            ends.minusSeconds(FUNDING_INTERVAL_HOURS * 3600L),
                            ZoneId.systemDefault());
                } catch (Exception ignored) {
                    // keep now
                }
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(rateHourly)
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .fundingPremium(fundingPremium)
                    .intervalHours(FUNDING_INTERVAL_HOURS)
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing Ondo Perps funding rate for {}: {}", normalizedSymbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal extractLatestPremium(JsonNode premiums) {
        if (premiums == null || !premiums.isArray() || premiums.isEmpty()) {
            return null;
        }
        // Premiums are typically newest-first
        JsonNode first = premiums.get(0);
        if (first != null && first.has("premiumIndex") && !first.get("premiumIndex").isNull()) {
            try {
                return new BigDecimal(first.get("premiumIndex").asText());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
