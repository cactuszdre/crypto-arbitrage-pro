package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO representing a single DEX rate for a symbol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DexRateDTO {
    private String dex;
    private BigDecimal rateAnnual;
}
