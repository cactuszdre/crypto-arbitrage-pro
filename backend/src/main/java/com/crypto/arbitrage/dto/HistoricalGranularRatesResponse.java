package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response wrapper for granular historical rates.
 * Returns daily data points instead of averages, enabling the frontend
 * to calculate spreads point-by-point for accurate historical returns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalGranularRatesResponse {
    
    /**
     * List of symbols with their daily rate history
     */
    private List<SymbolDailyRatesDTO> symbols;
    
    /**
     * Actual start date of data available in database
     */
    private LocalDate actualStartDate;
    
    /**
     * Actual end date of data available in database
     */
    private LocalDate actualEndDate;
    
    /**
     * Number of unique days of data used
     */
    private int dataPointCount;
    
    /**
     * Human-readable description of the period
     */
    private String periodLabel;
    
    /**
     * Requested number of days (for reference)
     */
    private int requestedDays;
}
