package com.crypto.funding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dex_market", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dex_id", "symbol"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DexMarket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dex_id", nullable = false)
    private Dex dex;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticker_id")
    private Ticker ticker;

    /**
     * The normalized symbol (e.g. BTC-USD-PERP)
     * Kept for backwards compatibility, but ticker reference is preferred.
     */
    @Column(nullable = false)
    private String symbol;
    
    /**
     * The raw symbol on the exchange if different (e.g. BTC_USDC)
     */
    @Column(name = "original_symbol")
    private String originalSymbol;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

