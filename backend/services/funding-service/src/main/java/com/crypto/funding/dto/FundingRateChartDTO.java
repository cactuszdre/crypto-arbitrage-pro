package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for 5-minute aggregated funding rate data optimized for charts.
 * Includes OHLC-style data for candlestick charts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRateChartDTO {
    
    private Long id;
    private String symbol;
    private String dexName;
    private LocalDateTime bucketTime;
    
    // Average values
    private BigDecimal avgRate;
    private BigDecimal avgRate8h;
    private BigDecimal avgRateAnnual;
    
    // OHLC values for candlestick charts
    private BigDecimal openRate;
    private BigDecimal closeRate;
    private BigDecimal minRate;  // Low
    private BigDecimal maxRate;  // High
    
    // Metadata
    private Integer sampleCount;
    
    /**
     * Convenience method to get rate change percentage within the bucket
     */
    public BigDecimal getRateChange() {
        if (openRate != null && closeRate != null && openRate.compareTo(BigDecimal.ZERO) != 0) {
            return closeRate.subtract(openRate)
                    .divide(openRate.abs(), 6, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }
}
