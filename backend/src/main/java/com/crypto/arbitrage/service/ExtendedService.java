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
 * Service for fetching funding rates from Extended.exchange (X10)
 * API Documentation: https://docs.extended.exchange/
 * 
 * Extended uses the /api/v1/info/markets endpoint which contains
 * funding rates in marketStats.fundingRate for each market.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtendedService implements DexFetcher {

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
    public List<FundingRate> fetchFundingData() {
        Optional<Dex> dexOpt = dexRepository.findByName(DEX_NAME);
        if (dexOpt.isEmpty()) {
            log.debug("DEX Extended not found in database");
            return List.of();
        }

        Dex dex = dexOpt.get();
        String baseUrl = dex.getBaseUrl() != null && !dex.getBaseUrl().isEmpty() 
                ? dex.getBaseUrl() 
                : DEFAULT_BASE_URL;

        try {
            // Configure WebClient with larger buffer for Extended's large response
            WebClient webClient = webClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer
                    .build();

            // Extended uses /api/v1/info/markets which returns all markets with funding info
            JsonNode response = webClient.get()
                    .uri("/api/v1/info/markets")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("Empty or invalid response from Extended");
                return List.of();
            }

            JsonNode marketsNode = response.get("data");
            if (!marketsNode.isArray()) {
                log.warn("Invalid response format from Extended (expected data array)");
                return List.of();
            }

            List<FundingRate> rates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode market : marketsNode) {
                FundingRate rate = parseMarketFundingRate(market, dex, now);
                if (rate != null) {
                    rates.add(rate);
                }
            }

            // Save all fetched rates
            if (!rates.isEmpty()) {
                fundingRateRepository.saveAll(rates);
                lastFundingService.upsertAll(rates);
                log.info("Extended: Fetched {} funding rates", rates.size());
            }

            return rates;

        } catch (Exception e) {
            log.error("Error fetching funding data from Extended: {}", e.getMessage());
            return List.of();
        }
    }

    private FundingRate parseMarketFundingRate(JsonNode market, Dex dex, LocalDateTime timestamp) {
        try {
            // Market structure:
            // { "name": "BTC-USD", "marketStats": { "fundingRate": "-0.000018", ... }, ... }
            
            if (!market.has("name") || !market.has("marketStats")) {
                return null;
            }

            String marketName = market.get("name").asText(); // e.g., "BTC-USD"
            JsonNode stats = market.get("marketStats");
            
            if (!stats.has("fundingRate")) {
                return null;
            }

            String fundingRateStr = stats.get("fundingRate").asText();
            
            // Skip invalid rates
            if (fundingRateStr == null || fundingRateStr.isEmpty() || 
                "nan".equalsIgnoreCase(fundingRateStr) || "null".equalsIgnoreCase(fundingRateStr)) {
                return null;
            }

            // Normalize symbol to standard format (e.g., BTC-USD-PERP)
            String symbol = marketName.endsWith("-PERP") ? marketName : marketName + "-PERP";

            // Get or create ticker
            Ticker ticker = tickerService.getOrCreate(symbol);

            // Parse funding rate (Extended returns hourly rate as decimal)
            BigDecimal rate = new BigDecimal(fundingRateStr);

            // Extended funding is hourly, so:
            // Rate 8h % = rate * 8 * 100
            BigDecimal rate8h = rate.multiply(BigDecimal.valueOf(8))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP);

            // Annual Rate % = rate * 24 * 365 * 100
            BigDecimal rateAnnual = rate.multiply(BigDecimal.valueOf(24 * 365))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            return FundingRate.builder()
                    .dex(dex)
                    .ticker(ticker)
                    .rate(rate)
                    .rate8h(rate8h)
                    .rateAnnual(rateAnnual)
                    .intervalHours(1) // Extended has hourly funding
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing Extended market: {}", e.getMessage());
            return null;
        }
    }
}
