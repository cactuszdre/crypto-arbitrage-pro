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
        @Param("startDate") LocalDate startDate
    );

    @Query("SELECT f FROM FundingRateDaily f WHERE f.date >= :startDate ORDER BY f.date DESC")
    List<FundingRateDaily> findAllSince(@Param("startDate") LocalDate startDate);

    boolean existsByTickerAndDexAndDate(Ticker ticker, Dex dex, LocalDate date);
}
