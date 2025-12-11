package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.PolymarketMarketDTO;
import com.crypto.arbitrage.dto.PolymarketPositionDTO;
import com.crypto.arbitrage.dto.PolymarketTradeLogDTO;
import com.crypto.arbitrage.service.PolymarketApiService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller for Polymarket data.
 * This provides READ-ONLY access to market data.
 * Trading is handled client-side via user's wallet.
 */
@RestController
@RequestMapping("/api/polymarket")
@RequiredArgsConstructor
@Slf4j
public class PolymarketController {
    
    private final PolymarketApiService polymarketApiService;
    
    /**
     * Get active 15-minute crypto markets (BTC/ETH up/down).
     * These are the markets suitable for the gabagool arbitrage strategy.
     */
    @GetMapping("/markets")
    public ResponseEntity<List<PolymarketMarketDTO>> getActiveMarkets() {
        log.info("Fetching active 15-min crypto markets");
        List<PolymarketMarketDTO> markets = polymarketApiService.fetch15MinCryptoMarkets();
        return ResponseEntity.ok(markets);
    }
    
    /**
     * Get markets with arbitrage opportunities (combined price < $1.00).
     */
    @GetMapping("/markets/arbitrage")
    public ResponseEntity<List<PolymarketMarketDTO>> getArbitrageOpportunities() {
        log.info("Fetching markets with arbitrage opportunities");
        List<PolymarketMarketDTO> opportunities = polymarketApiService.getArbitrageOpportunities();
        return ResponseEntity.ok(opportunities);
    }
    
    /**
     * Get order book for a specific token.
     * Useful for checking liquidity before trading.
     */
    @GetMapping("/orderbook/{tokenId}")
    public Mono<ResponseEntity<JsonNode>> getOrderBook(@PathVariable String tokenId) {
        log.info("Fetching order book for token: {}", tokenId);
        return polymarketApiService.getOrderBook(tokenId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Log a trade executed on the frontend.
     * This is for audit/history purposes only - no execution happens here.
     */
    @PostMapping("/trades/log")
    public ResponseEntity<Void> logTrade(
            @RequestBody PolymarketTradeLogDTO trade,
            @RequestHeader("X-Wallet-Address") String walletAddress) {
        
        log.info("Logging trade for wallet {}: {} {} {} shares @ ${}", 
                walletAddress, trade.getOrderType(), trade.getSide(), 
                trade.getShares(), trade.getPrice());
        
        // TODO: Implement trade logging to database
        // For now, just log it
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get user's positions (will be implemented with database).
     * For now returns empty list.
     */
    @GetMapping("/positions")
    public ResponseEntity<List<PolymarketPositionDTO>> getPositions(
            @RequestHeader("X-Wallet-Address") String walletAddress) {
        
        log.info("Fetching positions for wallet: {}", walletAddress);
        
        // TODO: Implement position fetching from database
        return ResponseEntity.ok(List.of());
    }
    
    /**
     * Health check for Polymarket integration.
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        try {
            List<PolymarketMarketDTO> markets = polymarketApiService.fetch15MinCryptoMarkets();
            return ResponseEntity.ok("OK - Found " + markets.size() + " active markets");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("ERROR: " + e.getMessage());
        }
    }
}
