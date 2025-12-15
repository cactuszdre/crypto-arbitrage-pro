package com.crypto.arbitrage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DexDTO {
    
    private Long id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private Integer fundingIntervalHours;
    private Boolean active;
    private String color;
    private String iconUrl;
    private String tradeUrlPattern;
    private LocalDateTime createdAt;
}
