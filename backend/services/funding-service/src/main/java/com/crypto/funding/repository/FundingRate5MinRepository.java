package com.crypto.funding.repository;

import com.crypto.funding.model.Dex;
import com.crypto.funding.model.FundingRate5Min;
import com.crypto.funding.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundingRate5MinRepository extends JpaRepository<FundingRate5Min, Long> {

    /**
     * Find a specific bucket by ticker, dex, and bucket time
     */
    Optional<FundingRate5Min> findByTickerAndDexAndBucketTime(Ticker ticker, Dex dex, LocalDateTime bucketTime);

    /**
     * Check if a bucket exists
     */
    boolean existsByTickerAndDexAndBucketTime(Ticker ticker, Dex dex, LocalDateTime bucketTime);

    /**
     * Get chart data for a specific ticker and dex within a time range
     */
    @Query("SELECT f FROM FundingRate5Min f WHERE f.ticker = :ticker AND f.dex = :dex " +
           "AND f.bucketTime >= :startTime AND f.bucketTime <= :endTime " +
           "ORDER BY f.bucketTime ASC")
    List<FundingRate5Min> findChartData(
        @Param("ticker") Ticker ticker,
        @Param("dex") Dex dex,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get chart data for a specific ticker across all DEXs
     */
    @Query("SELECT f FROM FundingRate5Min f WHERE f.ticker = :ticker " +
           "AND f.bucketTime >= :startTime AND f.bucketTime <= :endTime " +
           "ORDER BY f.bucketTime ASC, f.dex.name ASC")
    List<FundingRate5Min> findChartDataByTicker(
        @Param("ticker") Ticker ticker,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get chart data for all tickers on a specific DEX
     */
    @Query("SELECT f FROM FundingRate5Min f WHERE f.dex = :dex " +
           "AND f.bucketTime >= :startTime AND f.bucketTime <= :endTime " +
           "ORDER BY f.bucketTime ASC, f.ticker.normalizedSymbol ASC")
    List<FundingRate5Min> findChartDataByDex(
        @Param("dex") Dex dex,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get the latest N buckets for a ticker/dex combination
     */
    @Query("SELECT f FROM FundingRate5Min f WHERE f.ticker = :ticker AND f.dex = :dex " +
           "ORDER BY f.bucketTime DESC")
    List<FundingRate5Min> findLatestBuckets(
        @Param("ticker") Ticker ticker,
        @Param("dex") Dex dex,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Get all data since a specific time
     */
    @Query("SELECT f FROM FundingRate5Min f WHERE f.bucketTime >= :startTime ORDER BY f.bucketTime DESC")
    List<FundingRate5Min> findAllSince(@Param("startTime") LocalDateTime startTime);

    /**
     * Delete old 5-minute data (cleanup for data older than specified time)
     */
    @Modifying
    @Query("DELETE FROM FundingRate5Min f WHERE f.bucketTime < :cutoffTime")
    int deleteByBucketTimeBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count records by ticker
     */
    long countByTicker(Ticker ticker);

    /**
     * Get distinct bucket times for a ticker in a range (useful for gap detection)
     */
    @Query("SELECT DISTINCT f.bucketTime FROM FundingRate5Min f WHERE f.ticker = :ticker " +
           "AND f.bucketTime >= :startTime AND f.bucketTime <= :endTime " +
           "ORDER BY f.bucketTime ASC")
    List<LocalDateTime> findDistinctBucketTimes(
        @Param("ticker") Ticker ticker,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}
