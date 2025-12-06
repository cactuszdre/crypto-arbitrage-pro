package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dex")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "funding_interval_hours", nullable = false)
    private Integer fundingIntervalHours;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
