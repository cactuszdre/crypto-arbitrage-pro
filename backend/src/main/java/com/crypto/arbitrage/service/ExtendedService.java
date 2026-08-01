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

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtendedService implements DexFetcher, SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;

    private static final String DEX_NAME = "Extended";
    private static final String DEFAULT_BASE_URL = "https://api.starknet.extended.exchange";

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
            log.debug("DEX Extended not found in database");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = resolveBaseUrl(dex);

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri("/api/v1/info/markets")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data") || !response.get("data").isArray()) {
                log.warn("Empty or invalid response from Extended");
                return List.of();
            }

            List<FundingRate> rates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode market : response.get("data")) {
                FundingRate rate = parseMarketFundingRate(market, dex, now);
                if (rate != null) {
                    rates.add(rate);
                }
            }

            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                lastFundingService.upsertAll(rates);
            }
            return rates;
        } catch (Exception e) {
            log.error("Error fetching funding data from Extended: {}", e.getMessage());
            return List.of();
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
        Instant end = to != null ? to : Instant.now();

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(resolveBaseUrl(dex))
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/info/" + market + "/funding")
                            .queryParam("startTime", from.toEpochMilli())
                            .queryParam("endTime", end.toEpochMilli())
                            .queryParam("limit", 1000)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data") || !response.get("data").isArray()) {
                return List.of();
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);
            List<FundingSettlement> out = new ArrayList<>();

            for (JsonNode node : response.get("data")) {
                if (!node.has("f") || !node.has("T")) {
                    continue;
                }
                long timeMs = node.get("T").asLong();
                Instant paidAt = Instant.ofEpochMilli(timeMs);
                if (paidAt.isBefore(from) || !paidAt.isBefore(end)) {
                    continue;
                }
                BigDecimal hourly = new BigDecimal(node.get("f").asText());
                LocalDateTime settlementTime = LocalDateTime.ofInstant(paidAt, ZoneOffset.UTC)
                        .withMinute(0).withSecond(0).withNano(0);

                out.add(FundingSettlementService.buildFromHourly(
                        dex, ticker, settlementTime, 1, hourly,
                        "extended_funding_history", null, null));
            }
            return out;
        } catch (Exception e) {
            log.error("Extended funding history failed for {}: {}", normalizedSymbol, e.getMessage());
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

    private FundingRate parseMarketFundingRate(JsonNode market, Dex dex, LocalDateTime timestamp) {
        try {
            if (!market.has("name") || !market.has("marketStats")) {
                return null;
            }
            String marketName = market.get("name").asText();
            JsonNode stats = market.get("marketStats");
            if (!stats.has("fundingRate")) {
                return null;
            }

            String fundingRateStr = stats.get("fundingRate").asText();
            if (fundingRateStr == null || fundingRateStr.isEmpty()
                    || "nan".equalsIgnoreCase(fundingRateStr) || "null".equalsIgnoreCase(fundingRateStr)) {
                return null;
            }

            String symbol = marketName.endsWith("-PERP") ? marketName : marketName + "-PERP";
            Ticker ticker = tickerService.getOrCreate(symbol);
            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    new BigDecimal(fundingRateStr), FundingRateUnit.HOURLY, 1);

            LocalDateTime nextFunding = null;
            if (stats.has("nextFundingRate") && !stats.get("nextFundingRate").isNull()) {
                try {
                    long ms = stats.get("nextFundingRate").asLong();
                    nextFunding = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            if (nextFunding == null) {
                nextFunding = FundingSettlementService.nextHourUtc(
                        LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
            }

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
            log.debug("Error parsing Extended market: {}", e.getMessage());
            return null;
        }
    }

    private String resolveBaseUrl(Dex dex) {
        return dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() ? dex.getBaseUrl() : DEFAULT_BASE_URL;
    }

    static String toApiMarket(String normalizedSymbol) {
        if (normalizedSymbol == null) {
            return null;
        }
        if (normalizedSymbol.endsWith("-PERP")) {
            return normalizedSymbol.substring(0, normalizedSymbol.length() - 5);
        }
        return normalizedSymbol;
    }
}
