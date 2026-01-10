package com.crypto.funding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing the latest funding rate for each ticker/DEX combination.
 * This is a denormalized table optimized for quick lookups of current rates.
 * Updated on every funding rate fetch from DEXes.
 */
@Entity
@Table(name = "last_funding",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "dex_id"}),
    indexes = {
        @Index(name = "idx_last_funding_ticker", columnList = "ticker_id"),
        @Index(name = "idx_last_funding_dex", columnList = "dex_id"),
        @Index(name = "idx_last_funding_rate_annual", columnList = "rate_annual"),
        @Index(name = "idx_last_funding_timestamp", columnList = "timestamp")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LastFunding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

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
    @Builder.Default
    private Integer intervalHours = 1;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Helper method to get the normalized symbol from the ticker.
     */
    public String getSymbol() {
        return ticker != null ? ticker.getNormalizedSymbol() : null;
    }

    /**
     * Helper method to get the DEX name.
     */
    public String getDexName() {
        return dex != null ? dex.getName() : null;
    }

    /**
     * Create from a FundingRate entity
     */
    public static LastFunding fromFundingRate(FundingRate fr) {
        return LastFunding.builder()
                .ticker(fr.getTicker())
                .dex(fr.getDex())
                .rate(fr.getRate())
                .rate8h(fr.getRate8h())
                .rateAnnual(fr.getRateAnnual())
                .fundingPremium(fr.getFundingPremium())
                .fundingIndex(fr.getFundingIndex())
                .intervalHours(fr.getIntervalHours())
                .timestamp(fr.getTimestamp())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update this entity from a FundingRate
     */
    public void updateFrom(FundingRate fr) {
        this.rate = fr.getRate();
        this.rate8h = fr.getRate8h();
        this.rateAnnual = fr.getRateAnnual();
        this.fundingPremium = fr.getFundingPremium();
        this.fundingIndex = fr.getFundingIndex();
        this.intervalHours = fr.getIntervalHours();
        this.timestamp = fr.getTimestamp();
        this.updatedAt = LocalDateTime.now();
    }
}
