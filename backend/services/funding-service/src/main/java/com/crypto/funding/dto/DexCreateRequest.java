package com.crypto.funding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating/updating a DEX
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DexCreateRequest {
    
    private String name;
    private String baseUrl;
    private String apiKey;
    private Integer fundingIntervalHours;
    private Boolean active;
    private String color;
    private String iconUrl;
    private String tradeUrlPattern;
}
