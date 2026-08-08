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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class LighterService implements DexFetcher, SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;

    private static final String DEX_NAME = "Lighter";
    private static final String DEFAULT_BASE_URL = "https://mainnet.zklighter.elliot.ai/api/v1";

    /** symbol base (BTC) -> market_id */
    private final Map<String, Integer> marketIdCache = new ConcurrentHashMap<>();

    /**
     * The hourly backfill walks ~1500 markets, which Lighter answers with 429s.
     * Space out history calls and stand down entirely for a while after a 429.
     */
    private static final long HISTORY_MIN_INTERVAL_MS = 200;
    private static final long HISTORY_BACKOFF_MS = 120_000;
    private final AtomicLong nextHistorySlotMs = new AtomicLong(0L);
    private volatile long historyBackoffUntilMs = 0L;

    /**
     * Reserves the next evenly spaced request slot, then waits for it.
     *
     * @return false when Lighter is in cooldown and the call should be skipped.
     */
    private boolean awaitHistorySlot() {
        if (System.currentTimeMillis() < historyBackoffUntilMs) {
            return false;
        }
        long now = System.currentTimeMillis();
        long slot = nextHistorySlotMs.accumulateAndGet(
                now, (previous, current) -> Math.max(previous, current) + HISTORY_MIN_INTERVAL_MS)
                - HISTORY_MIN_INTERVAL_MS;

        long waitMs = slot - now;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return System.currentTimeMillis() >= historyBackoffUntilMs;
    }

    private void enterHistoryBackoff(String reason) {
        historyBackoffUntilMs = System.currentTimeMillis() + HISTORY_BACKOFF_MS;
        log.warn("Lighter rate limited ({}), pausing settled-history calls for {}s",
                reason, HISTORY_BACKOFF_MS / 1000);
    }

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override
    public FundingModel getFundingModel() {
        return FundingModel.DISCRETE_TWAP;
    }

    @Override
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX Lighter non trouvé en base de données");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = resolveBaseUrl(dex);

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(baseUrl).build();
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
            if (response.isObject()) {
                if (response.has("funding_rates")) {
                    ratesNode = response.get("funding_rates");
                } else if (response.has("rates")) {
                    ratesNode = response.get("rates");
                } else if (response.has("result")) {
                    ratesNode = response.get("result");
                }
            }

            if (!ratesNode.isArray()) {
                log.warn("Format de réponse invalide pour Lighter");
                return List.of();
            }

            Map<String, FundingRate> uniqueRates = new HashMap<>();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextFunding = FundingSettlementService.nextHourUtc(
                    LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));

            for (JsonNode node : ratesNode) {
                FundingRate rate = parseFundingRate(node, dex, now, nextFunding);
                if (rate != null) {
                    uniqueRates.put(rate.getSymbol(), rate);
                }
            }

            List<FundingRate> rates = new ArrayList<>(uniqueRates.values());
            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                lastFundingService.upsertAll(rates);
            }
            return rates;
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des funding data Lighter: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<FundingSettlement> fetchSettledHistory(String normalizedSymbol, Instant from, Instant to) {
        if (System.currentTimeMillis() < historyBackoffUntilMs) {
            return List.of();
        }
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return List.of();
        }
        Dex dex = dexOpt.get();
        String base = toBase(normalizedSymbol);
        Integer marketId = marketIdCache.get(base);
        if (marketId == null) {
            // Ensure cache is warm
            fetchFundingData();
            marketId = marketIdCache.get(base);
        }
        if (marketId == null) {
            return List.of();
        }

        if (!awaitHistorySlot()) {
            return List.of();
        }

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(resolveBaseUrl(dex)).build();
            long startTs = from.getEpochSecond();
            long endTs = to != null ? to.getEpochSecond() : Instant.now().getEpochSecond();

            Integer finalMarketId = marketId;
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fundings")
                            .queryParam("market_id", finalMarketId)
                            .queryParam("resolution", "1h")
                            .queryParam("start_timestamp", startTs)
                            .queryParam("end_timestamp", endTs)
                            .queryParam("count_back", 168)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("fundings") || !response.get("fundings").isArray()) {
                return List.of();
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);
            List<FundingSettlement> out = new ArrayList<>();

            for (JsonNode node : response.get("fundings")) {
                if (!node.has("rate") || !node.has("timestamp")) {
                    continue;
                }
                long ts = node.get("timestamp").asLong();
                // API may return seconds
                Instant paidAt = ts > 10_000_000_000L ? Instant.ofEpochMilli(ts) : Instant.ofEpochSecond(ts);
                if (paidAt.isBefore(from) || (to != null && !paidAt.isBefore(to))) {
                    continue;
                }

                // Same unit as /funding-rates: 8h-quoted decimal (apply direction consistently)
                BigDecimal eightHourQuote = signedEightHourQuote(
                        new BigDecimal(node.get("rate").asText()), node);
                NormalizedFundingRate n = FundingRateNormalizer.normalize(
                        eightHourQuote, FundingRateUnit.EIGHT_HOUR, 1);

                LocalDateTime settlementTime = LocalDateTime.ofInstant(paidAt, ZoneOffset.UTC)
                        .withMinute(0).withSecond(0).withNano(0);

                out.add(FundingSettlement.builder()
                        .dex(dex)
                        .ticker(ticker)
                        .settlementTime(settlementTime)
                        .intervalHours(1)
                        .rate(n.getHourly())
                        .rate8h(n.getRate8hPercent())
                        .rateAnnual(n.getRateAnnualPercent())
                        .source("lighter_fundings")
                        .build());
            }
            return out;
        } catch (WebClientResponseException.TooManyRequests e) {
            enterHistoryBackoff("429 on " + normalizedSymbol);
            return List.of();
        } catch (Exception e) {
            log.error("Lighter fundings history failed for {}: {}", normalizedSymbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<MarketScheduleSnapshot> fetchSchedule(String normalizedSymbol) {
        return Optional.of(MarketScheduleSnapshot.builder()
                .intervalHours(1)
                .nextFundingTime(FundingSettlementService.nextHourUtc(
                        LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)))
                .continuous(false)
                .build());
    }

    private FundingRate parseFundingRate(JsonNode node, Dex dex, LocalDateTime timestamp,
                                         LocalDateTime nextFunding) {
        try {
            if (node.has("exchange") && !"lighter".equalsIgnoreCase(node.get("exchange").asText())) {
                return null;
            }

            String rawSymbol = node.get("symbol").asText();
            String baseSymbol = rawSymbol.split("_")[0];
            if (node.has("market_id")) {
                marketIdCache.put(baseSymbol, node.get("market_id").asInt());
            }

            String symbol = baseSymbol + "-USD-PERP";
            BigDecimal eightHourQuote = signedEightHourQuote(
                    new BigDecimal(node.get("rate").asText()), node);
            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    eightHourQuote, FundingRateUnit.EIGHT_HOUR, 1);
            Ticker ticker = tickerService.getOrCreate(symbol);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(n.getHourly())
                    .rate8h(n.getRate8hPercent())
                    .rateAnnual(n.getRateAnnualPercent())
                    .intervalHours(1)
                    .nextFundingTime(nextFunding)
                    .timestamp(timestamp)
                    .build();
        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate Lighter: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Lighter rate sign for both live and history endpoints.
     * If {@code direction} is present: long = longs pay (positive), short = shorts pay (negative).
     * If absent, keep the API rate as already signed.
     */
    static BigDecimal signedEightHourQuote(BigDecimal rate, JsonNode node) {
        if (rate == null) {
            return null;
        }
        if (node != null && node.has("direction") && !node.get("direction").isNull()) {
            String direction = node.get("direction").asText();
            if ("short".equalsIgnoreCase(direction)) {
                return rate.abs().negate();
            }
            if ("long".equalsIgnoreCase(direction)) {
                return rate.abs();
            }
        }
        return rate;
    }

    private String resolveBaseUrl(Dex dex) {
        return dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() ? dex.getBaseUrl() : DEFAULT_BASE_URL;
    }

    static String toBase(String normalizedSymbol) {
        if (normalizedSymbol == null) {
            return null;
        }
        if (normalizedSymbol.endsWith("-USD-PERP")) {
            return normalizedSymbol.substring(0, normalizedSymbol.length() - "-USD-PERP".length());
        }
        return normalizedSymbol;
    }
}
