package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Wrapper DTO for historical rates by symbol with metadata about the data range.
 * Used to enable frontend-side calculation of best arbitrage routes
 * based on user-selected DEX filters with historical data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalRatesResponse {
    
    /**
     * List of symbols with their average rates across DEXes
     */
    private List<SymbolRatesDTO> symbols;
    
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
