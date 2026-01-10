package com.crypto.funding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "funding_rate_daily", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "dex_id", "date"}),
    indexes = {
        @Index(name = "idx_date", columnList = "date"),
        @Index(name = "idx_ticker_date", columnList = "ticker_id, date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRateDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "avg_rate", nullable = false, precision = 20, scale = 12)
    private BigDecimal avgRate;

    @Column(name = "min_rate", precision = 20, scale = 12)
    private BigDecimal minRate;

    @Column(name = "max_rate", precision = 20, scale = 12)
    private BigDecimal maxRate;

    @Column(name = "avg_rate_8h", precision = 20, scale = 12)
    private BigDecimal avgRate8h;

    @Column(name = "avg_rate_annual", precision = 20, scale = 4)
    private BigDecimal avgRateAnnual;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Helper method to get the normalized symbol from the ticker.
     */
    public String getSymbol() {
        return ticker != null ? ticker.getNormalizedSymbol() : null;
    }
}
