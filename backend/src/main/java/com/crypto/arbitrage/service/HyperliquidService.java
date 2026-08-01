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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HyperliquidService implements DexFetcher, SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String DEX_NAME = "Hyperliquid";
    private static final String DEFAULT_BASE_URL = "https://api.hyperliquid.xyz";

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override
    public FundingModel getFundingModel() {
        return FundingModel.DISCRETE_TWAP;
    }

    /**
     * Current accruing/predicted funding rates for all perpetual assets.
     */
    @Override
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.error("DEX Hyperliquid non trouvé en base de données");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = resolveBaseUrl(dex);

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(baseUrl).build();

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
            LocalDateTime nextFunding = FundingSettlementService.nextHourUtc(
                    LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));

            for (int i = 0; i < universeNode.size() && i < assetCtxsNode.size(); i++) {
                FundingRate rate = parseFundingRate(universeNode.get(i), assetCtxsNode.get(i), dex, now, nextFunding);
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
            log.error("Erreur lors de la récupération des funding data Hyperliquid: {}", e.getMessage(), e);
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
        String coin = toCoin(normalizedSymbol);
        if (coin == null) {
            return List.of();
        }

        try {
            WebClient webClient = webClientBuilder.clone().baseUrl(resolveBaseUrl(dex)).build();

            Map<String, Object> body = new HashMap<>();
            body.put("type", "fundingHistory");
            body.put("coin", coin);
            body.put("startTime", from.toEpochMilli());
            if (to != null) {
                body.put("endTime", to.toEpochMilli());
            }

            JsonNode response = webClient.post()
                    .uri("/info")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.isArray()) {
                return List.of();
            }

            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);
            List<FundingSettlement> out = new ArrayList<>();
            long toMs = to != null ? to.toEpochMilli() : Long.MAX_VALUE;

            for (JsonNode node : response) {
                if (!node.has("fundingRate") || !node.has("time")) {
                    continue;
                }
                long timeMs = node.get("time").asLong();
                if (timeMs < from.toEpochMilli() || timeMs >= toMs) {
                    continue;
                }
                BigDecimal hourly = new BigDecimal(node.get("fundingRate").asText());
                LocalDateTime settlementTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC)
                        .withMinute(0).withSecond(0).withNano(0);

                BigDecimal premium = null;
                if (node.has("premium") && !node.get("premium").isNull()) {
                    String p = node.get("premium").asText();
                    if (p != null && !p.isEmpty() && !"nan".equalsIgnoreCase(p)) {
                        premium = new BigDecimal(p);
                    }
                }

                out.add(FundingSettlementService.buildFromHourly(
                        dex, ticker, settlementTime, 1, hourly,
                        "hyperliquid_fundingHistory", premium, null));
            }
            return out;
        } catch (Exception e) {
            log.error("Hyperliquid fundingHistory failed for {}: {}", normalizedSymbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<MarketScheduleSnapshot> fetchSchedule(String normalizedSymbol) {
        LocalDateTime next = FundingSettlementService.nextHourUtc(
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        return Optional.of(MarketScheduleSnapshot.builder()
                .intervalHours(1)
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
            WebClient webClient = webClientBuilder.clone().baseUrl(resolveBaseUrl(dex)).build();
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
                    symbols.add(asset.get("name").asText() + "-USD-PERP");
                }
            }
            return symbols;
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des symboles Hyperliquid: {}", e.getMessage());
            return List.of();
        }
    }

    private FundingRate parseFundingRate(JsonNode assetMeta, JsonNode assetCtx, Dex dex,
                                         LocalDateTime timestamp, LocalDateTime nextFunding) {
        try {
            if (assetMeta == null || assetCtx == null || !assetMeta.has("name") || !assetCtx.has("funding")) {
                return null;
            }
            String fundingStr = assetCtx.get("funding").asText();
            if (fundingStr == null || fundingStr.isEmpty()
                    || "nan".equalsIgnoreCase(fundingStr) || "null".equalsIgnoreCase(fundingStr)) {
                return null;
            }

            String symbol = assetMeta.get("name").asText() + "-USD-PERP";
            Ticker ticker = tickerService.getOrCreate(symbol);
            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    new BigDecimal(fundingStr), FundingRateUnit.HOURLY, 1);

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
                    .rate(n.getHourly())
                    .rate8h(n.getRate8hPercent())
                    .rateAnnual(n.getRateAnnualPercent())
                    .fundingPremium(premium)
                    .intervalHours(1)
                    .nextFundingTime(nextFunding)
                    .timestamp(timestamp)
                    .build();
        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate Hyperliquid: {}", e.getMessage());
            return null;
        }
    }

    private String resolveBaseUrl(Dex dex) {
        return dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() ? dex.getBaseUrl() : DEFAULT_BASE_URL;
    }

    static String toCoin(String normalizedSymbol) {
        if (normalizedSymbol == null) {
            return null;
        }
        if (normalizedSymbol.endsWith("-USD-PERP")) {
            return normalizedSymbol.substring(0, normalizedSymbol.length() - "-USD-PERP".length());
        }
        return normalizedSymbol;
    }
}
