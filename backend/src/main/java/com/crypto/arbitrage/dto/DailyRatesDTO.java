package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO representing all DEX rates for a single date.
 * Used for granular historical data to enable accurate spread calculations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRatesDTO {
    private LocalDate date;
    private List<DexRateDTO> rates;
}
