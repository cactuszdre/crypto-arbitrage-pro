package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing 1-hour aggregated funding rate data.
 * Used for 1D, 3D, and 7D charts to reduce data volume.
 * Aggregated from raw 10-second funding rate data for maximum precision.
 */
@Entity
@Table(name = "funding_rate_hourly", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "dex_id", "bucket_time"}),
    indexes = {
        @Index(name = "idx_funding_hourly_bucket_time", columnList = "bucket_time"),
        @Index(name = "idx_funding_hourly_ticker_bucket", columnList = "ticker_id, bucket_time"),
        @Index(name = "idx_funding_hourly_dex_bucket", columnList = "dex_id, bucket_time"),
        @Index(name = "idx_funding_hourly_ticker_dex_bucket", columnList = "ticker_id, dex_id, bucket_time")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRateHourly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    /**
     * The start time of the 1-hour bucket (e.g., 10:00, 11:00, 12:00...)
     */
    @Column(name = "bucket_time", nullable = false)
    private LocalDateTime bucketTime;

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

    /**
     * The first rate value in this 1-hour bucket (for candlestick charts)
     */
    @Column(name = "open_rate", precision = 20, scale = 12)
    private BigDecimal openRate;

    /**
     * The last rate value in this 1-hour bucket (for candlestick charts)
     */
    @Column(name = "close_rate", precision = 20, scale = 12)
    private BigDecimal closeRate;

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
    
    /**
     * Helper method to get the DEX name.
     */
    public String getDexName() {
        return dex != null ? dex.getName() : null;
    }
}
