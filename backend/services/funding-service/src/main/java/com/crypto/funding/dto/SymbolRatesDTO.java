package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing all DEX rates for a single symbol.
 * Used to enable frontend-side calculation of best arbitrage routes
 * based on user-selected DEX filters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolRatesDTO {
    private String symbol;
    private List<DexRateDTO> rates;
}
