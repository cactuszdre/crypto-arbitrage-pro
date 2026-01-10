package com.crypto.funding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "arbitrage_opportunity", indexes = {
    @Index(name = "idx_ticker_time", columnList = "ticker_id, detected_at")
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

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

    /**
     * Helper method to get the normalized symbol from the ticker.
     */
    public String getSymbol() {
        return ticker != null ? ticker.getNormalizedSymbol() : null;
    }
}

