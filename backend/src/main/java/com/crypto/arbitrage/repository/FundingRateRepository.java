package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import org.springframework.data.domain.Page;
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
    
    List<FundingRate> findBySymbolOrderByTimestampDesc(String symbol);
    
    List<FundingRate> findByDexAndSymbolOrderByTimestampDesc(Dex dex, String symbol);
    
    @Query("SELECT f FROM FundingRate f WHERE f.dex = :dex AND f.symbol = :symbol ORDER BY f.timestamp DESC")
    List<FundingRate> findLatestByDexAndSymbol(@Param("dex") Dex dex, @Param("symbol") String symbol, Pageable pageable);
    
    @Query("SELECT f FROM FundingRate f WHERE f.timestamp >= :startDate ORDER BY f.timestamp DESC")
    List<FundingRate> findByTimestampAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT f FROM FundingRate f WHERE f.dex = :dex AND f.symbol = :symbol AND f.timestamp >= :startDate ORDER BY f.timestamp ASC")
    List<FundingRate> findHistoricalData(
        @Param("dex") Dex dex, 
        @Param("symbol") String symbol, 
        @Param("startDate") LocalDateTime startDate
    );
    
    @Query("SELECT DISTINCT f.symbol FROM FundingRate f WHERE f.dex = :dex")
    List<String> findDistinctSymbolsByDex(@Param("dex") Dex dex);
    
    Optional<FundingRate> findFirstByDexAndSymbolOrderByTimestampDesc(Dex dex, String symbol);

    @Query("SELECT f FROM FundingRate f WHERE f.id IN (SELECT MAX(f2.id) FROM FundingRate f2 GROUP BY f2.dex, f2.symbol)")
    List<FundingRate> findAllLatestRates();
}
