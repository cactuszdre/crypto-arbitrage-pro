package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Wrapper DTO for historical arbitrage opportunities with metadata about the data range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalArbitrageResponse {
    
    /**
     * The list of arbitrage opportunities
     */
    private List<HistoricalArbitrageDTO> opportunities;
    
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
     * e.g., "Yesterday (Dec 11, 2024)" or "Dec 5 - Dec 11, 2024 (7 days)"
     */
    private String periodLabel;
    
    /**
     * Requested number of days (for reference)
     */
    private int requestedDays;
}
