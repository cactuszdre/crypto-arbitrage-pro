package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for logging trades from frontend.
 * Trades are executed client-side, this is for audit/history only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolymarketTradeLogDTO {
    
    private String marketId;
    private String marketQuestion;
    private String side;        // "YES" or "NO"
    private String orderType;   // "BUY" or "SELL"
    private BigDecimal shares;
    private BigDecimal price;
    private BigDecimal totalCost;
    private String txHash;
    private LocalDateTime executedAt;
    
    // Position info after trade
    private BigDecimal newYesShares;
    private BigDecimal newNoShares;
    private BigDecimal newImbalanceRatio;
}
