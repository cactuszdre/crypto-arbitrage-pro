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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParadexService implements SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;
    private final EncryptionService encryptionService;

    private static final String DEX_NAME = "Paradex";
    
    // Cache for market info (symbol -> funding_period_hours)
    private final Map<String, Integer> marketFundingPeriodCache = new ConcurrentHashMap<>();
    
    // Track when market info was last loaded (for periodic refresh)
    private volatile long lastMarketInfoLoadTime = 0;
    private static final long MARKET_INFO_REFRESH_INTERVAL_MS = 3600_000; // Refresh every hour

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override
    public FundingModel getFundingModel() {
        return FundingModel.CONTINUOUS_INDEX;
    }

    @Override
    public boolean supportsSettledHistory() {
        // Continuous accrual — no discrete paid settlement print to backfill.
        return false;
    }

    @Override
    public List<FundingSettlement> fetchSettledHistory(String normalizedSymbol, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public Optional<MarketScheduleSnapshot> fetchSchedule(String normalizedSymbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        String baseUrl = dexOpt.map(Dex::getBaseUrl).orElse("");
        int intervalHours = getFundingPeriodHours(normalizedSymbol, baseUrl);
        return Optional.of(MarketScheduleSnapshot.builder()
                .intervalHours(intervalHours)
                .nextFundingTime(null)
                .continuous(true)
                .build());
    }

    private WebClient paradexClient(String baseUrl) {
        return webClientBuilder.clone()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Loads market info from Paradex API and caches funding_period_hours for each symbol.
     * This is needed because different pairs have different funding periods.
     * Now all Paradex markets use 8h funding periods.
     */
    public void loadMarketInfo() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            return;
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl();

        try {
            WebClient webClient = paradexClient(baseUrl);

            JsonNode response = webClient.get()
                    .uri("/markets")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("results")) {
                // Avoid retry storm when response is empty/invalid
                lastMarketInfoLoadTime = System.currentTimeMillis();
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
            
            // Update last load time on success
            lastMarketInfoLoadTime = System.currentTimeMillis();
            log.debug("Loaded {} Paradex market funding periods", marketFundingPeriodCache.size());

        } catch (Exception e) {
            // Prevent per-symbol retry storms when /markets exceeds buffer or fails
            lastMarketInfoLoadTime = System.currentTimeMillis();
            log.error("Error loading Paradex market info: {}", e.getMessage());
        }
    }

    /**
     * Gets the funding period hours for a symbol, loading market info if not cached or stale.
     * Cache is refreshed every hour to pick up any changes from Paradex.
     */
    private int getFundingPeriodHours(String symbol, String baseUrl) {
        long now = System.currentTimeMillis();
        boolean cacheExpired = (now - lastMarketInfoLoadTime) > MARKET_INFO_REFRESH_INTERVAL_MS;
        
        if (marketFundingPeriodCache.isEmpty() || cacheExpired) {
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
            WebClient webClient = paradexClient(baseUrl);

            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/funding/data")
                            .queryParam("market", symbol)
                            .build())
                    .header("Authorization", "Bearer " + decryptedApiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(java.time.Duration.ofSeconds(10)) // Fail fast if API hangs
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

            return rates;

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                log.warn("Paradex 503 Service Unavailable for {}: {}", symbol, e.getStatusCode());
            } else {
                log.error("Erreur WebClient Paradex pour {}: {} - {}", symbol, e.getStatusCode(), e.getMessage());
            }
            return List.of();
        } catch (Exception e) {
            // Check if it is a timeout wrapped in RuntimeException
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.warn("Timeout lors de la récupération Paradex pour {}", symbol);
                return List.of();
            }
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
        
        // Update last_funding table
        lastFundingService.upsertFromFundingRate(saved);
        
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
            WebClient webClient = paradexClient(baseUrl);

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
            WebClient webClient = paradexClient(baseUrl);

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
            BigDecimal periodRate = new BigDecimal(node.get("funding_rate").asText());
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

            // The funding_rate from Paradex API is the rate for the funding period.
            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    periodRate, FundingRateUnit.PERIOD, fundingPeriodHours);

            Ticker ticker = tickerService.getOrCreate(symbol);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(n.getHourly())
                    .rate8h(n.getRate8hPercent())
                    .rateAnnual(n.getRateAnnualPercent())
                    .fundingPremium(fundingPremium)
                    .fundingIndex(fundingIndex)
                    .intervalHours(fundingPeriodHours)
                    .nextFundingTime(null) // continuous index — no discrete next funding
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors du parsing du funding rate: {}", e.getMessage());
            return null;
        }
    }
}
