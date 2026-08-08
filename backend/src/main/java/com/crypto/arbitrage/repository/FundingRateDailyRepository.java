package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundingRateDailyRepository extends JpaRepository<FundingRateDaily, Long> {

    Optional<FundingRateDaily> findByTickerAndDexAndDate(Ticker ticker, Dex dex, LocalDate date);

    List<FundingRateDaily> findByTickerAndDexOrderByDateDesc(Ticker ticker, Dex dex);

    @Query("SELECT f FROM FundingRateDaily f WHERE f.ticker = :ticker AND f.date >= :startDate ORDER BY f.date DESC")
    List<FundingRateDaily> findByTickerSince(@Param("ticker") Ticker ticker, @Param("startDate") LocalDate startDate);

    @Query("SELECT f FROM FundingRateDaily f WHERE f.ticker = :ticker AND f.dex = :dex AND f.date >= :startDate ORDER BY f.date ASC")
    List<FundingRateDaily> findHistoricalData(
            @Param("ticker") Ticker ticker,
            @Param("dex") Dex dex,
            @Param("startDate") LocalDate startDate);

    /**
     * Average annual rate and sample count per symbol and DEX since {@code startDate},
     * as {symbol, dexName, avgRate, sampleCount}.
     * <p>
     * Each symbol is restricted to the range where every one of its DEXes has data
     * (the most recent per-DEX first date), so the averages stay comparable.
     * <p>
     * Doing this in SQL matters: loading the rows as entities meant ~260k objects on
     * a 512 MB heap, which ran the backend out of memory for the longer periods.
     */
    @Query(value = """
        WITH scoped AS (
            SELECT ticker_id, dex_id, date, avg_rate_annual
            FROM funding_rate_daily
            WHERE date >= :startDate
        ),
        per_dex_start AS (
            SELECT ticker_id, dex_id, MIN(date) AS first_date
            FROM scoped
            GROUP BY ticker_id, dex_id
        ),
        common_start AS (
            SELECT ticker_id, MAX(first_date) AS start_date
            FROM per_dex_start
            GROUP BY ticker_id
        )
        SELECT t.normalized_symbol,
               d.name,
               SUM(COALESCE(s.avg_rate_annual, 0)) / COUNT(*) AS avg_rate,
               COUNT(*) AS sample_count
        FROM scoped s
        JOIN common_start c ON c.ticker_id = s.ticker_id AND s.date >= c.start_date
        JOIN ticker t ON t.id = s.ticker_id
        JOIN dex d ON d.id = s.dex_id
        GROUP BY t.normalized_symbol, d.name
        ORDER BY t.normalized_symbol
        """, nativeQuery = true)
    List<Object[]> aggregateRatesPerSymbolAndDexSince(@Param("startDate") LocalDate startDate);

    boolean existsByTickerAndDexAndDate(Ticker ticker, Dex dex, LocalDate date);

    /**
     * All rows for one date, so the daily job can check for duplicates in memory
     * instead of one query per ticker/dex pair.
     */
    List<FundingRateDaily> findByDate(LocalDate date);

    /**
     * Find the minimum (oldest) date in the database since a given start date
     */
    @Query("SELECT MIN(f.date) FROM FundingRateDaily f WHERE f.date >= :startDate")
    LocalDate findMinDateSince(@Param("startDate") LocalDate startDate);

    /**
     * Find the maximum (most recent) date in the database
     */
    @Query("SELECT MAX(f.date) FROM FundingRateDaily f")
    LocalDate findMaxDate();

    /**
     * Count distinct dates available since a given start date
     */
    @Query("SELECT COUNT(DISTINCT f.date) FROM FundingRateDaily f WHERE f.date >= :startDate")
    int countDistinctDatesSince(@Param("startDate") LocalDate startDate);
}
