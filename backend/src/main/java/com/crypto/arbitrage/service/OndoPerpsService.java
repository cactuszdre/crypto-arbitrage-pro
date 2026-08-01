package com.crypto.arbitrage.service;

import com.crypto.arbitrage.funding.FundingModel;
import com.crypto.arbitrage.funding.FundingRateNormalizer;
import com.crypto.arbitrage.funding.FundingRateUnit;
import com.crypto.arbitrage.funding.MarketScheduleSnapshot;
import com.crypto.arbitrage.funding.NormalizedFundingRate;
import com.crypto.arbitrage.funding.SettledFundingProvider;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ondo Perps funding: predicted via /funding_rates, settled via /funding_rate_history.
 * Settles hourly on UTC hour boundaries (TWAP of 60 minute premiums / 8).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OndoPerpsService implements SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;

    private static final String DEX_NAME = "Ondo Perps";
    private static final int FUNDING_INTERVAL_HOURS = 1;

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override
    public FundingModel getFundingModel() {
        return FundingModel.DISCRETE_TWAP;
    }

    public Optional<FundingRate> fetchAndSaveLatestFundingRate(String normalizedSymbol) {
        Optional<FundingRate> rate = fetchFundingData(normalizedSymbol);
        if (rate.isEmpty()) {
            return Optional.empty();
        }
        FundingRate saved = fundingRateRepository.save(rate.get());
        lastFundingService.upsertFromFundingRate(saved);
        return Optional.of(saved);
    }

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

            return Optional.ofNullable(parseFundingRate(result, dex, normalizedSymbol));
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

    @Override
    public List<FundingSettlement> fetchSettledHistory(String normalizedSymbol, Instant from, Instant to) {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }
        Dex dex = dexOpt.get();
        String market = toApiMarket(normalizedSymbol);

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(dex.getBaseUrl()).build();

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder
                                .path("/v1/perps/funding_rate_history")
                                .queryParam("market", market)
                                .queryParam("limit", 1000)
                                .queryParam("startTime", from.toEpochMilli());
                        if (to != null) {
                            b = b.queryParam("endTime", to.toEpochMilli());
                        }
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(java.time.Duration.ofSeconds(20))
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                return List.of();
            }

            JsonNode results = response.get("result");
            if (results == null || !results.isArray()) {
                return List.of();
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);
            List<FundingSettlement> out = new ArrayList<>();
            Instant toBound = to != null ? to : Instant.MAX;

            for (JsonNode node : results) {
                if (!node.has("fundingRate") || !node.has("time")) {
                    continue;
                }
                Instant paidAt = Instant.parse(node.get("time").asText());
                if (paidAt.isBefore(from) || !paidAt.isBefore(toBound)) {
                    continue;
                }
                BigDecimal hourly = new BigDecimal(node.get("fundingRate").asText());
                LocalDateTime settlementTime = LocalDateTime.ofInstant(paidAt, ZoneOffset.UTC)
                        .withMinute(0).withSecond(0).withNano(0);

                out.add(FundingSettlementService.buildFromHourly(
                        dex, ticker, settlementTime, FUNDING_INTERVAL_HOURS, hourly,
                        "ondo_funding_rate_history", null, null));
            }
            return out;
        } catch (Exception e) {
            log.error("Ondo funding_rate_history failed for {}: {}", normalizedSymbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<MarketScheduleSnapshot> fetchSchedule(String normalizedSymbol) {
        Optional<FundingRate> rate = fetchFundingData(normalizedSymbol);
        if (rate.isPresent() && rate.get().getNextFundingTime() != null) {
            return Optional.of(MarketScheduleSnapshot.builder()
                    .intervalHours(FUNDING_INTERVAL_HOURS)
                    .nextFundingTime(rate.get().getNextFundingTime())
                    .continuous(false)
                    .build());
        }
        LocalDateTime next = FundingSettlementService.nextHourUtc(
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        return Optional.of(MarketScheduleSnapshot.builder()
                .intervalHours(FUNDING_INTERVAL_HOURS)
                .nextFundingTime(next)
                .continuous(false)
                .build());
    }

    public List<String> fetchAvailableSymbols() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }
        Dex dex = dexOpt.get();
        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(dex.getBaseUrl())
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
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
                String m = pair.path("market").asText(null);
                if (m != null && !m.isBlank()) {
                    symbols.add(normalizeSymbol(m));
                }
            }
            return symbols;
        } catch (Exception e) {
            log.error("Error fetching Ondo Perps markets: {}", e.getMessage());
            return List.of();
        }
    }

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
            // API `rate` is the estimated hourly funding for the current interval (not yet settled).
            BigDecimal rateHourly = new BigDecimal(result.get("rate").asText());
            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    rateHourly, FundingRateUnit.HOURLY, FUNDING_INTERVAL_HOURS);

            BigDecimal fundingPremium = extractLatestPremium(result.get("premiums"));

            LocalDateTime timestamp = LocalDateTime.now();
            LocalDateTime nextFunding = null;
            if (result.has("intervalEnds") && !result.get("intervalEnds").isNull()) {
                try {
                    Instant ends = Instant.parse(result.get("intervalEnds").asText());
                    nextFunding = LocalDateTime.ofInstant(ends, ZoneOffset.UTC);
                    timestamp = LocalDateTime.now();
                } catch (Exception ignored) {
                    // keep now
                }
            }
            if (nextFunding == null) {
                nextFunding = FundingSettlementService.nextHourUtc(
                        LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(n.getHourly())
                    .rate8h(n.getRate8hPercent())
                    .rateAnnual(n.getRateAnnualPercent())
                    .fundingPremium(fundingPremium)
                    .intervalHours(FUNDING_INTERVAL_HOURS)
                    .nextFundingTime(nextFunding)
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
