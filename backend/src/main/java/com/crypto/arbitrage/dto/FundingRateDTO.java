package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRateDTO {
    
    private Long id;
    private String dexName;
    private String symbol;
    private BigDecimal rate;
    private BigDecimal rate8h;
    private BigDecimal rateAnnual;
    private BigDecimal fundingPremium;
    private Integer intervalHours;
    private LocalDateTime timestamp;
    /** Next expected funding payment (predicted track). */
    private LocalDateTime nextFundingTime;
    /** PREDICTED vs SETTLED for clients that need to distinguish. */
    private String rateKind;
    private String direction; // "LONGS_PAY" or "SHORTS_PAY"
}
