package com.crypto.funding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "funding_rate", indexes = {
    @Index(name = "idx_dex_ticker_time", columnList = "dex_id, ticker_id, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(nullable = false, precision = 20, scale = 12)
    private BigDecimal rate;

    @Column(name = "rate_8h", precision = 20, scale = 12)
    private BigDecimal rate8h;

    @Column(name = "rate_annual", precision = 20, scale = 4)
    private BigDecimal rateAnnual;

    @Column(name = "funding_premium", precision = 20, scale = 8)
    private BigDecimal fundingPremium;

    @Column(name = "funding_index", precision = 30, scale = 12)
    private BigDecimal fundingIndex;

    @Column(name = "interval_hours", nullable = false)
    private Integer intervalHours;

    @Column(nullable = false)
    private LocalDateTime timestamp;

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

