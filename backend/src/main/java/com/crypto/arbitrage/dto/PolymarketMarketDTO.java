package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * DTO for Polymarket market data.
 * Represents a binary prediction market (YES/NO outcomes).
 * 
 * IMPORTANT: endDate is stored as Instant (UTC) to ensure proper timezone handling.
 * The Polymarket API returns dates in UTC, and we must compare with Instant.now()
 * to get accurate time remaining regardless of server timezone.
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
    private Instant endDate;  // Changed from LocalDateTime to Instant for proper timezone handling
    private String category;
    
    // Pricing
    private BigDecimal yesPrice;
    private BigDecimal noPrice;
    private BigDecimal combinedPrice;
    
    // Market stats
    private BigDecimal volume24h;
    private BigDecimal liquidity;
    private LocalDateTime lastUpdated;
    
    // Classic arbitrage indicator (YES + NO < $1.00)
    private boolean hasArbitrageOpportunity;
    private BigDecimal potentialProfit;
    
    // Gabagool Strategy indicators
    private String entryQuality;  // "EXCELLENT", "GOOD", "FAIR", "POOR"
    private String cheapSide;     // "YES", "NO", or "BALANCED"
    private BigDecimal cheapPrice; // Price of the cheaper side
    private BigDecimal spreadFromFair; // How far from 50/50 (potential for reversal)
    
    // Time until resolution
    private Long secondsRemaining;
    
    /**
     * Calculate combined price, entry quality, and Gabagool metrics.
     * 
     * Gabagool Strategy Logic:
     * - Look for markets where one side is significantly cheaper than expected
     * - A price below 0.45 on either side is a good entry opportunity
     * - The strategy profits from buying both sides cheap over time
     */
    public void calculateArbitrageMetrics() {
        if (yesPrice != null && noPrice != null) {
            this.combinedPrice = yesPrice.add(noPrice);
            
            // Classic arbitrage check (rare)
            BigDecimal one = BigDecimal.ONE;
            if (combinedPrice.compareTo(one) < 0) {
                this.hasArbitrageOpportunity = true;
                this.potentialProfit = one.subtract(combinedPrice);
            } else {
                this.hasArbitrageOpportunity = false;
                this.potentialProfit = BigDecimal.ZERO;
            }
            
            // Gabagool Strategy: Entry Quality Analysis
            calculateEntryQuality();
        }
    }
    
    /**
     * Calculate entry quality for Gabagool strategy.
     * Lower prices = better entry opportunities.
     */
    private void calculateEntryQuality() {
        BigDecimal fairValue = new BigDecimal("0.50");
        BigDecimal excellentThreshold = new BigDecimal("0.40"); // Below 40¢ = excellent
        BigDecimal goodThreshold = new BigDecimal("0.45");      // Below 45¢ = good
        BigDecimal fairThreshold = new BigDecimal("0.48");      // Below 48¢ = fair
        
        // Find the cheaper side
        if (yesPrice.compareTo(noPrice) < 0) {
            this.cheapSide = "YES";
            this.cheapPrice = yesPrice;
        } else if (noPrice.compareTo(yesPrice) < 0) {
            this.cheapSide = "NO";
            this.cheapPrice = noPrice;
        } else {
            this.cheapSide = "BALANCED";
            this.cheapPrice = yesPrice;
        }
        
        // Calculate spread from fair value (50/50)
        this.spreadFromFair = yesPrice.subtract(fairValue).abs()
                .setScale(4, RoundingMode.HALF_UP);
        
        // Determine entry quality based on cheap side price
        if (cheapPrice.compareTo(excellentThreshold) <= 0) {
            this.entryQuality = "EXCELLENT";
            this.hasArbitrageOpportunity = true; // Flag as opportunity
            this.potentialProfit = fairValue.subtract(cheapPrice);
        } else if (cheapPrice.compareTo(goodThreshold) <= 0) {
            this.entryQuality = "GOOD";
            this.hasArbitrageOpportunity = true;
            this.potentialProfit = fairValue.subtract(cheapPrice);
        } else if (cheapPrice.compareTo(fairThreshold) <= 0) {
            this.entryQuality = "FAIR";
        } else {
            this.entryQuality = "POOR";
        }
    }
    
    /**
     * Calculate seconds remaining until market resolution.
     * Uses Instant for proper UTC-based time comparison.
     */
    public void calculateTimeRemaining() {
        if (endDate != null) {
            Instant now = Instant.now();
            if (endDate.isAfter(now)) {
                this.secondsRemaining = Duration.between(now, endDate).getSeconds();
            } else {
                this.secondsRemaining = 0L;
            }
        }
    }
}

