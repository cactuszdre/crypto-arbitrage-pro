package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_funding_schedule", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dex_id", "ticker_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketFundingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "interval_hours", nullable = false)
    @Builder.Default
    private Integer intervalHours = 1;

    @Column(name = "next_funding_time")
    private LocalDateTime nextFundingTime;

    /** Paradex-style continuous accrual (no discrete settlement print). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean continuous = false;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
