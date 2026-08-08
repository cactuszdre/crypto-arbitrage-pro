package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundingRateRepository extends JpaRepository<FundingRate, Long> {
    
    List<FundingRate> findByDexOrderByTimestampDesc(Dex dex);
    
    List<FundingRate> findByTickerOrderByTimestampDesc(Ticker ticker);
    
    List<FundingRate> findByDexAndTickerOrderByTimestampDesc(Dex dex, Ticker ticker);
    
    @Query("SELECT f FROM FundingRate f WHERE f.dex = :dex AND f.ticker = :ticker ORDER BY f.timestamp DESC")
    List<FundingRate> findLatestByDexAndTicker(@Param("dex") Dex dex, @Param("ticker") Ticker ticker, Pageable pageable);
    
    @Query("SELECT f FROM FundingRate f WHERE f.timestamp >= :startDate ORDER BY f.timestamp DESC")
    List<FundingRate> findByTimestampAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT f FROM FundingRate f WHERE f.dex = :dex AND f.ticker = :ticker AND f.timestamp >= :startDate ORDER BY f.timestamp ASC")
    List<FundingRate> findHistoricalData(
        @Param("dex") Dex dex, 
        @Param("ticker") Ticker ticker, 
        @Param("startDate") LocalDateTime startDate
    );
    
    @Query("SELECT DISTINCT f.ticker FROM FundingRate f WHERE f.dex = :dex")
    List<Ticker> findDistinctTickersByDex(@Param("dex") Dex dex);
    
    Optional<FundingRate> findFirstByDexAndTickerOrderByTimestampDesc(Dex dex, Ticker ticker);

    @Query("SELECT f FROM FundingRate f WHERE f.id IN (SELECT MAX(f2.id) FROM FundingRate f2 GROUP BY f2.dex, f2.ticker)")
    List<FundingRate> findAllLatestRates();

    // Cleanup methods
    @Query("SELECT f FROM FundingRate f WHERE f.timestamp < :cutoffTime")
    List<FundingRate> findByTimestampBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Modifying
    @Query("DELETE FROM FundingRate f WHERE f.timestamp < :cutoffTime")
    int deleteByTimestampBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Deletes at most {@code batchSize} expired rows. The daily cleanup removes
     * millions of rows; doing it in one statement holds a pooled connection and
     * the table lock for minutes, which starves web traffic.
     */
    @Modifying
    @Query(value = """
        DELETE FROM funding_rate
        WHERE ctid IN (
            SELECT ctid FROM funding_rate WHERE timestamp < :cutoffTime LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteBatchByTimestampBefore(@Param("cutoffTime") LocalDateTime cutoffTime,
                                     @Param("batchSize") int batchSize);

    // Aggregation query - returns Object[] with: tickerId, dexId, avgRate, minRate, maxRate, avgRate8h, avgRateAnnual, count
    @Query("""
        SELECT f.ticker.id, f.dex.id, 
               AVG(f.rate), MIN(f.rate), MAX(f.rate), 
               AVG(f.rate8h), AVG(f.rateAnnual), COUNT(f)
        FROM FundingRate f 
        WHERE f.timestamp < :cutoffTime
        GROUP BY f.ticker.id, f.dex.id
        """)
    List<Object[]> aggregateOldRates(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Aggregation query for 5-minute buckets.
     * Returns Object[] with: tickerId, dexId, avgRate, minRate, maxRate, avgRate8h, avgRateAnnual, count, firstRate, lastRate
     */
    @Query("""
        SELECT f.ticker.id, f.dex.id, 
               AVG(f.rate), MIN(f.rate), MAX(f.rate), 
               AVG(f.rate8h), AVG(f.rateAnnual), COUNT(f)
        FROM FundingRate f 
        WHERE f.timestamp >= :startTime AND f.timestamp < :endTime
        GROUP BY f.ticker.id, f.dex.id
        """)
    List<Object[]> aggregateForBucket(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get the first rate in a time bucket for open rate calculation
     */
    @Query("""
        SELECT f FROM FundingRate f 
        WHERE f.ticker.id = :tickerId AND f.dex.id = :dexId 
        AND f.timestamp >= :startTime AND f.timestamp < :endTime
        ORDER BY f.timestamp ASC
        """)
    List<FundingRate> findFirstInBucket(
        @Param("tickerId") Integer tickerId,
        @Param("dexId") Long dexId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * Get the last rate in a time bucket for close rate calculation
     */
    @Query("""
        SELECT f FROM FundingRate f 
        WHERE f.ticker.id = :tickerId AND f.dex.id = :dexId 
        AND f.timestamp >= :startTime AND f.timestamp < :endTime
        ORDER BY f.timestamp DESC
        """)
    List<FundingRate> findLastInBucket(
        @Param("tickerId") Integer tickerId,
        @Param("dexId") Long dexId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );
}


