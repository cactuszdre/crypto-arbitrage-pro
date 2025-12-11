package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for user's position in a Polymarket market.
 * Includes health metrics for risk management.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolymarketPositionDTO {
    
    private Long id;
    private String walletAddress;
    private String marketId;
    private String marketQuestion;
    private LocalDateTime marketEndTime;
    
    // Shares held
    private BigDecimal yesShares;
    private BigDecimal noShares;
    
    // Cost basis
    private BigDecimal yesCost;
    private BigDecimal noCost;
    private BigDecimal totalCost;
    private BigDecimal avgCombinedCost;
    
    // Current market prices
    private BigDecimal currentYesPrice;
    private BigDecimal currentNoPrice;
    
    // Position health
    private BigDecimal imbalanceRatio;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Long secondsRemaining;
    
    // P&L
    private BigDecimal unrealizedPnl;
    private BigDecimal projectedPnl;
    
    // Status
    private String status; // OPEN, CLOSED, RESOLVED
    private String resolution; // YES, NO (if resolved)
    private BigDecimal finalPnl;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Calculate position health metrics.
     */
    public void calculateHealth() {
        // Calculate imbalance ratio
        if (yesShares != null && noShares != null) {
            BigDecimal max = yesShares.max(noShares);
            if (max.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = yesShares.subtract(noShares).abs();
                this.imbalanceRatio = diff.divide(max, 4, java.math.RoundingMode.HALF_UP);
            } else {
                this.imbalanceRatio = BigDecimal.ZERO;
            }
        }
        
        // Calculate total cost
        if (yesCost != null && noCost != null) {
            this.totalCost = yesCost.add(noCost);
        }
        
        // Determine risk level based on imbalance and time remaining
        determineRiskLevel();
    }
    
    private void determineRiskLevel() {
        if (imbalanceRatio == null || secondsRemaining == null) {
            this.riskLevel = "UNKNOWN";
            return;
        }
        
        double imbalance = imbalanceRatio.doubleValue();
        long seconds = secondsRemaining;
        
        // Risk matrix based on imbalance and time
        if (seconds < 120) { // < 2 minutes
            this.riskLevel = imbalance > 0.1 ? "CRITICAL" : "HIGH";
        } else if (seconds < 300) { // < 5 minutes
            if (imbalance > 0.25) this.riskLevel = "CRITICAL";
            else if (imbalance > 0.15) this.riskLevel = "HIGH";
            else this.riskLevel = "MEDIUM";
        } else if (seconds < 600) { // < 10 minutes
            if (imbalance > 0.20) this.riskLevel = "HIGH";
            else if (imbalance > 0.10) this.riskLevel = "MEDIUM";
            else this.riskLevel = "LOW";
        } else { // >= 10 minutes
            if (imbalance > 0.30) this.riskLevel = "MEDIUM";
            else this.riskLevel = "LOW";
        }
    }
    
    /**
     * Calculate projected P&L at resolution.
     * Assumes the side with more shares wins.
     */
    public void calculateProjectedPnl() {
        if (yesShares == null || noShares == null || totalCost == null) {
            return;
        }
        
        // Minimum hedged shares = profit guaranteed
        BigDecimal hedgedShares = yesShares.min(noShares);
        BigDecimal hedgedValue = hedgedShares; // Each pair = $1.00 at resolution
        
        // Unhedged shares = at risk
        BigDecimal unhedgedYes = yesShares.subtract(hedgedShares);
        BigDecimal unhedgedNo = noShares.subtract(hedgedShares);
        
        // Best case: if the side with more shares wins
        BigDecimal bestCase = hedgedValue.add(unhedgedYes.max(unhedgedNo));
        
        // Projected profit from hedged portion
        this.projectedPnl = hedgedValue.subtract(totalCost);
    }
}
