package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "arbitrage_opportunity", indexes = {
    @Index(name = "idx_symbol_time", columnList = "symbol, detected_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArbitrageOpportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dex1_id", nullable = false)
    private Dex dex1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dex2_id", nullable = false)
    private Dex dex2;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "dex1_rate", nullable = false, precision = 20, scale = 12)
    private BigDecimal dex1Rate;

    @Column(name = "dex2_rate", nullable = false, precision = 20, scale = 12)
    private BigDecimal dex2Rate;

    @Column(nullable = false, precision = 20, scale = 12)
    private BigDecimal spread;

    @Column(name = "projected_daily_return", precision = 20, scale = 8)
    private BigDecimal projectedDailyReturn;

    @Column(name = "recommended_long_dex", length = 50)
    private String recommendedLongDex;

    @Column(name = "recommended_short_dex", length = 50)
    private String recommendedShortDex;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
}
