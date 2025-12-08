package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

