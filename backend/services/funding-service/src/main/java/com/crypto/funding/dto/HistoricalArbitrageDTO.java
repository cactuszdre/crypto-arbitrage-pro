package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalArbitrageDTO {
    
    private String symbol;
    private String longDex;
    private String shortDex;
    private BigDecimal longRate;   // Average annual rate on long DEX
    private BigDecimal shortRate;  // Average annual rate on short DEX
    private BigDecimal spread;     // Difference between rates
    private BigDecimal projectedDailyReturn;
    private Integer sampleCount;   // Number of days of data
    private LocalDate startDate;
    private LocalDate endDate;
}
