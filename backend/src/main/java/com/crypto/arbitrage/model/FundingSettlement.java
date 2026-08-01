package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Actually paid funding rate at a settlement boundary (truth for history/charts).
 */
@Entity
@Table(name = "funding_settlement", indexes = {
        @Index(name = "idx_funding_settlement_ticker_time", columnList = "ticker_id, settlement_time"),
        @Index(name = "idx_funding_settlement_dex_time", columnList = "dex_id, settlement_time")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dex_id", "ticker_id", "settlement_time"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "settlement_time", nullable = false)
    private LocalDateTime settlementTime;

    @Column(name = "interval_hours", nullable = false)
    @Builder.Default
    private Integer intervalHours = 1;

    /** Hourly decimal rate (canonical). */
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

    /** API / capture source, e.g. hyperliquid_fundingHistory */
    @Column(nullable = false, length = 64)
    private String source;

    /** Last predicted hourly rate just before settlement (sanity check). */
    @Column(name = "predicted_rate", precision = 20, scale = 12)
    private BigDecimal predictedRate;

    /** (predicted - settled) in basis points of hourly rate * 10000. */
    @Column(name = "predicted_delta_bps", precision = 20, scale = 6)
    private BigDecimal predictedDeltaBps;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getSymbol() {
        return ticker != null ? ticker.getNormalizedSymbol() : null;
    }

    public String getDexName() {
        return dex != null ? dex.getName() : null;
    }
}
