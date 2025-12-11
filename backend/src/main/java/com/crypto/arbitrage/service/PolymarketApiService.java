package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.PolymarketMarketDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching market data from Polymarket APIs.
 * This is READ-ONLY - no trading operations (those happen client-side).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolymarketApiService {
    
    private final WebClient.Builder webClientBuilder;
    
    private static final String GAMMA_API = "https://gamma-api.polymarket.com";
    private static final String CLOB_API = "https://clob.polymarket.com";
    
    /**
     * Fetch active 15-minute crypto markets (BTC/ETH up/down).
     * These are the markets suitable for the gabagool arbitrage strategy.
     */
    @Cacheable(value = "polymarketCrypto15Min", unless = "#result.isEmpty()")
    public List<PolymarketMarketDTO> fetch15MinCryptoMarkets() {
        log.info("Fetching 15-min crypto markets from Polymarket");
        
        try {
            WebClient client = webClientBuilder.clone().baseUrl(GAMMA_API).build();
            
            // Fetch markets with crypto tag
            JsonNode response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/markets")
                            .queryParam("active", "true")
                            .queryParam("closed", "false")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            
            if (response == null || !response.isArray()) {
                log.warn("No markets returned from Polymarket API");
                return new ArrayList<>();
            }
            
            List<PolymarketMarketDTO> markets = new ArrayList<>();
            
            for (JsonNode marketNode : response) {
                String question = marketNode.path("question").asText("");
                String category = marketNode.path("category").asText("");
                
                // Filter for 15-minute crypto markets
                if (is15MinCryptoMarket(question, category)) {
                    PolymarketMarketDTO market = parseMarket(marketNode);
                    if (market != null) {
                        market.calculateArbitrageMetrics();
                        market.calculateTimeRemaining();
                        markets.add(market);
                    }
                }
            }
            
            log.info("Found {} active 15-min crypto markets", markets.size());
            return markets;
            
        } catch (Exception e) {
            log.error("Error fetching Polymarket markets: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Fetch all active markets with arbitrage opportunities.
     * Returns markets where YES + NO combined price < $1.00.
     */
    public List<PolymarketMarketDTO> getArbitrageOpportunities() {
        List<PolymarketMarketDTO> allMarkets = fetch15MinCryptoMarkets();
        
        return allMarkets.stream()
                .filter(PolymarketMarketDTO::isHasArbitrageOpportunity)
                .sorted((a, b) -> b.getPotentialProfit().compareTo(a.getPotentialProfit()))
                .toList();
    }
    
    /**
     * Get order book for a specific market token.
     * Useful for checking liquidity before trading.
     */
    public Mono<JsonNode> getOrderBook(String tokenId) {
        WebClient client = webClientBuilder.clone().baseUrl(CLOB_API).build();
        
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/book")
                        .queryParam("token_id", tokenId)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnError(e -> log.error("Error fetching order book for {}: {}", tokenId, e.getMessage()));
    }
    
    /**
     * Check if a market is a 15-minute crypto up/down market.
     */
    private boolean is15MinCryptoMarket(String question, String category) {
        if (question == null) return false;
        
        String q = question.toLowerCase();
        
        // Check for Bitcoin or Ethereum up/down patterns
        boolean isCrypto = q.contains("bitcoin") || q.contains("btc") || 
                          q.contains("ethereum") || q.contains("eth");
        boolean isUpDown = q.contains("up or down") || q.contains("above") || q.contains("below");
        boolean is15Min = q.contains("15 min") || q.contains("15-min") || 
                         q.contains(":00am") || q.contains(":15am") || 
                         q.contains(":30am") || q.contains(":45am") ||
                         q.contains(":00pm") || q.contains(":15pm") || 
                         q.contains(":30pm") || q.contains(":45pm");
        
        return isCrypto && (isUpDown || is15Min);
    }
    
    /**
     * Parse a market JSON node into PolymarketMarketDTO.
     */
    private PolymarketMarketDTO parseMarket(JsonNode node) {
        try {
            String marketId = node.path("id").asText();
            String question = node.path("question").asText();
            String conditionId = node.path("conditionId").asText();
            String category = node.path("category").asText();
            
            // Parse outcome prices
            String outcomePricesStr = node.path("outcomePrices").asText("[]");
            BigDecimal yesPrice = BigDecimal.ZERO;
            BigDecimal noPrice = BigDecimal.ZERO;
            
            if (outcomePricesStr.startsWith("[")) {
                // Parse JSON array format
                JsonNode pricesNode = node.path("outcomePrices");
                if (pricesNode.isArray() && pricesNode.size() >= 2) {
                    yesPrice = new BigDecimal(pricesNode.get(0).asText("0"));
                    noPrice = new BigDecimal(pricesNode.get(1).asText("0"));
                } else if (pricesNode.isTextual()) {
                    // Handle string format like "[\"0.45\", \"0.55\"]"
                    String cleaned = outcomePricesStr.replaceAll("[\\[\\]\"]", "");
                    String[] parts = cleaned.split(",");
                    if (parts.length >= 2) {
                        yesPrice = new BigDecimal(parts[0].trim());
                        noPrice = new BigDecimal(parts[1].trim());
                    }
                }
            }
            
            // Parse token IDs
            String clobTokenIds = node.path("clobTokenIds").asText("[]");
            String tokenIdYes = "";
            String tokenIdNo = "";
            if (clobTokenIds.startsWith("[")) {
                String cleaned = clobTokenIds.replaceAll("[\\[\\]\"]", "");
                String[] parts = cleaned.split(",");
                if (parts.length >= 2) {
                    tokenIdYes = parts[0].trim();
                    tokenIdNo = parts[1].trim();
                }
            }
            
            // Parse end date
            LocalDateTime endDate = null;
            String endDateStr = node.path("endDate").asText("");
            if (!endDateStr.isEmpty()) {
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(endDateStr);
                    endDate = zdt.toLocalDateTime();
                } catch (Exception e) {
                    log.debug("Could not parse end date: {}", endDateStr);
                }
            }
            
            // Parse volume and liquidity
            BigDecimal volume24h = BigDecimal.ZERO;
            BigDecimal liquidity = BigDecimal.ZERO;
            try {
                volume24h = new BigDecimal(node.path("volume24hr").asText("0"));
                liquidity = new BigDecimal(node.path("liquidity").asText("0"));
            } catch (NumberFormatException e) {
                // Ignore
            }
            
            return PolymarketMarketDTO.builder()
                    .marketId(marketId)
                    .question(question)
                    .conditionId(conditionId)
                    .tokenIdYes(tokenIdYes)
                    .tokenIdNo(tokenIdNo)
                    .endDate(endDate)
                    .category(category)
                    .yesPrice(yesPrice)
                    .noPrice(noPrice)
                    .volume24h(volume24h)
                    .liquidity(liquidity)
                    .lastUpdated(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing market: {}", e.getMessage());
            return null;
        }
    }
}
