package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticker", indexes = {
    @Index(name = "idx_base_symbol", columnList = "base_symbol"),
    @Index(name = "idx_normalized_symbol", columnList = "normalized_symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "base_symbol", nullable = false, length = 20)
    private String baseSymbol;

    @Column(name = "normalized_symbol", nullable = false, unique = true, length = 50)
    private String normalizedSymbol;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
