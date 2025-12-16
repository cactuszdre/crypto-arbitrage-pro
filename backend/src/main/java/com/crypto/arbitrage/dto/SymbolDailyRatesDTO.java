package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a symbol with its daily rate history.
 * Contains granular daily data for each DEX to enable accurate spread calculations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolDailyRatesDTO {
    private String symbol;
    private List<DailyRatesDTO> dailyRates;
}
