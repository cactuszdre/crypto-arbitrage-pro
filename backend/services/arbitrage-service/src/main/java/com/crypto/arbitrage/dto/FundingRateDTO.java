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
    private String direction; // "LONGS_PAY" or "SHORTS_PAY"
}
