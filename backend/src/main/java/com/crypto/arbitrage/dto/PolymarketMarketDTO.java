package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Polymarket market data.
 * Represents a binary prediction market (YES/NO outcomes).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolymarketMarketDTO {
    
    private String marketId;
    private String question;
    private String conditionId;
    private String tokenIdYes;
    private String tokenIdNo;
    private LocalDateTime endDate;
    private String category;
    
    // Pricing
    private BigDecimal yesPrice;
    private BigDecimal noPrice;
    private BigDecimal combinedPrice;
    
    // Market stats
    private BigDecimal volume24h;
    private BigDecimal liquidity;
    private LocalDateTime lastUpdated;
    
    // Arbitrage opportunity indicator
    private boolean hasArbitrageOpportunity;
    private BigDecimal potentialProfit;
    
    // Time until resolution
    private Long secondsRemaining;
    
    /**
     * Calculate combined price and potential profit.
     */
    public void calculateArbitrageMetrics() {
        if (yesPrice != null && noPrice != null) {
            this.combinedPrice = yesPrice.add(noPrice);
            // If combined < 1.00, there's arbitrage opportunity
            BigDecimal one = BigDecimal.ONE;
            if (combinedPrice.compareTo(one) < 0) {
                this.hasArbitrageOpportunity = true;
                this.potentialProfit = one.subtract(combinedPrice);
            } else {
                this.hasArbitrageOpportunity = false;
                this.potentialProfit = BigDecimal.ZERO;
            }
        }
    }
    
    /**
     * Calculate seconds remaining until market resolution.
     */
    public void calculateTimeRemaining() {
        if (endDate != null) {
            LocalDateTime now = LocalDateTime.now();
            if (endDate.isAfter(now)) {
                this.secondsRemaining = java.time.Duration.between(now, endDate).getSeconds();
            } else {
                this.secondsRemaining = 0L;
            }
        }
    }
}
