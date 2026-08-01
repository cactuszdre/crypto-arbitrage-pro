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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service for fetching funding rates from Nado.xyz (on Ink L2)
 * API Documentation: https://docs.nado.xyz/developer-resources/api
 * 
 * Nado uses two main endpoints:
 * 1. Gateway API (symbols): GET /v1/query?type=symbols&product_type=perp
 * 2. Archive API (funding rates): POST with {"funding_rates": {"product_ids": [...]}}
 * 
 * The funding_rate_x18 is a 24hr rate multiplied by 10^18.
 * Funding is settled hourly but the rate is expressed as 24hr rate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NadoService implements DexFetcher, SettledFundingProvider {

    private final DexRepository dexRepository;
    private final FundingRateRepository fundingRateRepository;
    private final LastFundingService lastFundingService;
    private final TickerService tickerService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String DEX_NAME = "Nado";
    private static final String DEFAULT_GATEWAY_URL = "https://gateway.prod.nado.xyz";
    private static final String ARCHIVE_URL = "https://archive.prod.nado.xyz/v1";
    
    // Divisor for x18 values (10^18)
    private static final BigDecimal X18_DIVISOR = new BigDecimal("1000000000000000000");

    @Override
    public String getDexName() {
        return DEX_NAME;
    }

    @Override
    public FundingModel getFundingModel() {
        return FundingModel.PREDICTED_SNAPSHOT;
    }

    @Override
    public List<FundingSettlement> fetchSettledHistory(String normalizedSymbol, Instant from, Instant to) {
        return List.of();
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

    @Override
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.debug("DEX Nado not found in database");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String gatewayUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty()
                ? dex.getBaseUrl()
                : DEFAULT_GATEWAY_URL;

        try {
            // Step 1: Fetch all perp symbols to get product IDs
            Map<Integer, String> productIdToSymbol = fetchPerpSymbols(gatewayUrl);
            
            if (productIdToSymbol.isEmpty()) {
                log.warn("No perpetual symbols found from Nado");
                return List.of();
            }


            // Step 2: Fetch funding rates for all product IDs
            List<Integer> productIds = new ArrayList<>(productIdToSymbol.keySet());
            Map<Integer, BigDecimal> fundingRates = fetchFundingRates(productIds);

            if (fundingRates.isEmpty()) {
                log.warn("No funding rates returned from Nado");
                return List.of();
            }

            // Step 3: Build FundingRate objects
            List<FundingRate> rates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextFunding = FundingSettlementService.nextHourUtc(
                    LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));

            for (Map.Entry<Integer, BigDecimal> entry : fundingRates.entrySet()) {
                Integer productId = entry.getKey();
                BigDecimal fundingRateValue = entry.getValue();
                String symbol = productIdToSymbol.get(productId);

                if (symbol != null) {
                    FundingRate rate = buildFundingRate(symbol, fundingRateValue, dex, now, nextFunding);
                    if (rate != null) {
                        rates.add(rate);
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
            log.error("Error fetching funding data from Nado: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Fetch all perpetual symbols and their product IDs from the Gateway API.
     * GET /v1/query?type=symbols&product_type=perp
     */
    private Map<Integer, String> fetchPerpSymbols(String gatewayUrl) {
        Map<Integer, String> productIdToSymbol = new HashMap<>();

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(gatewayUrl)
                    .defaultHeader("Accept-Encoding", "gzip")
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024))
                    .build();

            JsonNode response = webClient.get()
                    .uri("/v1/query?type=symbols&product_type=perp")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("status") || 
                !"success".equals(response.get("status").asText())) {
                log.warn("Invalid response from Nado symbols API");
                return productIdToSymbol;
            }

            JsonNode data = response.get("data");
            if (data == null || !data.has("symbols")) {
                log.warn("No symbols data in Nado response");
                return productIdToSymbol;
            }

            JsonNode symbols = data.get("symbols");
            Iterator<Map.Entry<String, JsonNode>> fields = symbols.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode symbolInfo = entry.getValue();
                
                if (symbolInfo.has("product_id") && symbolInfo.has("symbol") &&
                    "perp".equals(symbolInfo.path("type").asText())) {
                    int productId = symbolInfo.get("product_id").asInt();
                    String symbol = symbolInfo.get("symbol").asText();
                    productIdToSymbol.put(productId, symbol);
                }
            }

        } catch (Exception e) {
            log.error("Error fetching symbols from Nado: {}", e.getMessage());
        }

        return productIdToSymbol;
    }

    /**
     * Fetch funding rates for given product IDs from the Archive API.
     * POST https://archive.prod.nado.xyz/v1 with {"funding_rates": {"product_ids": [...]}}
     */
    private Map<Integer, BigDecimal> fetchFundingRates(List<Integer> productIds) {
        Map<Integer, BigDecimal> fundingRates = new HashMap<>();

        try {
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(ARCHIVE_URL)
                    .defaultHeader("Accept-Encoding", "gzip")
                    .defaultHeader("Content-Type", "application/json")
                    .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024))
                    .build();

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> fundingRatesRequest = new HashMap<>();
            fundingRatesRequest.put("product_ids", productIds);
            requestBody.put("funding_rates", fundingRatesRequest);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("Nado funding rates request: {}", requestJson);

            JsonNode response = webClient.post()
                    .uri("")
                    .body(Mono.just(requestJson), String.class)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Empty response from Nado funding rates API");
                return fundingRates;
            }

            // Response format: { "2": { "product_id": 2, "funding_rate_x18": "...", "update_time": "..." }, ... }
            Iterator<Map.Entry<String, JsonNode>> fields = response.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode rateInfo = entry.getValue();
                
                if (rateInfo.has("product_id") && rateInfo.has("funding_rate_x18")) {
                    int productId = rateInfo.get("product_id").asInt();
                    String fundingRateX18 = rateInfo.get("funding_rate_x18").asText();
                    
                    try {
                        // Convert from x18 format to decimal
                        BigDecimal rateX18 = new BigDecimal(fundingRateX18);
                        BigDecimal rate = rateX18.divide(X18_DIVISOR, 18, RoundingMode.HALF_UP);
                        fundingRates.put(productId, rate);
                    } catch (NumberFormatException e) {
                        log.debug("Invalid funding rate value for product {}: {}", productId, fundingRateX18);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error fetching funding rates from Nado Archive API: {}", e.getMessage());
        }

        return fundingRates;
    }

    /**
     * Build a FundingRate object from the symbol and rate data.
     * Nado's funding_rate_x18 is a 24hr (daily) rate; settled hourly.
     */
    private FundingRate buildFundingRate(String symbol, BigDecimal dailyRate, Dex dex,
                                         LocalDateTime timestamp, LocalDateTime nextFunding) {
        try {
            String normalizedSymbol = normalizeSymbol(symbol);
            Ticker ticker = tickerService.getOrCreate(normalizedSymbol);

            NormalizedFundingRate n = FundingRateNormalizer.normalize(
                    dailyRate, FundingRateUnit.DAILY, 1);

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
            log.debug("Error building funding rate for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Nado symbol format to the standard format used in this application.
     * Nado uses: BTC-PERP, ETH-PERP, etc.
     * Our format: BTC-USD-PERP (standard across other DEXes)
     */
    private String normalizeSymbol(String symbol) {
        // Convert Nado's format (BTC-PERP) to standard format (BTC-USD-PERP)
        if (symbol.endsWith("-PERP")) {
            // Extract base symbol (e.g., "BTC" from "BTC-PERP")
            String base = symbol.substring(0, symbol.length() - 5); // Remove "-PERP"
            return base + "-USD-PERP";
        }
        return symbol + "-USD-PERP";
    }
}
