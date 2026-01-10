package com.crypto.funding.repository;

import com.crypto.funding.model.Dex;
import com.crypto.funding.model.LastFunding;
import com.crypto.funding.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface LastFundingRepository extends JpaRepository<LastFunding, Long> {

    /**
     * Delete all entries that haven't been updated since the given timestamp.
     * Used to clean up stale entries for delisted pairs.
     */
    @Modifying
    @Query("DELETE FROM LastFunding lf WHERE lf.updatedAt < :threshold")
    int deleteByUpdatedAtBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * Find latest funding for a specific ticker and dex
     */
    Optional<LastFunding> findByTickerAndDex(Ticker ticker, Dex dex);

    /**
     * Find all latest funding rates for a specific ticker (across all DEXes)
     */
    List<LastFunding> findByTicker(Ticker ticker);

    /**
     * Find all latest funding rates for a specific DEX
     */
    List<LastFunding> findByDex(Dex dex);

    /**
     * Find by ticker ID and dex ID (useful for upsert operations)
     */
    @Query("SELECT lf FROM LastFunding lf WHERE lf.ticker.id = :tickerId AND lf.dex.id = :dexId")
    Optional<LastFunding> findByTickerIdAndDexId(
        @Param("tickerId") Integer tickerId,
        @Param("dexId") Long dexId
    );

    /**
     * Get all rates ordered by annual rate descending (best shorts first)
     */
    @Query("SELECT lf FROM LastFunding lf ORDER BY lf.rateAnnual DESC")
    List<LastFunding> findAllOrderByRateAnnualDesc();

    /**
     * Get all rates for a ticker ordered by annual rate
     */
    @Query("SELECT lf FROM LastFunding lf WHERE lf.ticker = :ticker ORDER BY lf.rateAnnual DESC")
    List<LastFunding> findByTickerOrderByRateAnnualDesc(@Param("ticker") Ticker ticker);

    /**
     * Count by DEX (for diagnostics)
     */
    long countByDex(Dex dex);

    /**
     * Get distinct tickers that have funding rates
     */
    @Query("SELECT DISTINCT lf.ticker FROM LastFunding lf")
    List<Ticker> findDistinctTickers();

    /**
     * Get distinct DEXes that have funding rates
     */
    @Query("SELECT DISTINCT lf.dex FROM LastFunding lf")
    List<Dex> findDistinctDexes();
}
