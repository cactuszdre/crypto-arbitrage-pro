package com.crypto.funding.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ArbitrageOpportunityDTO {
    private String symbol;
    private String longDex;
    private String shortDex;
    private BigDecimal longRate;
    private BigDecimal shortRate;
    private BigDecimal spread;
    private BigDecimal projectedDailyReturn;
    private LocalDateTime detectedAt;
}
