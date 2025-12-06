package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.ArbitrageOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArbitrageOpportunityRepository extends JpaRepository<ArbitrageOpportunity, Long> {
    
    List<ArbitrageOpportunity> findBySymbolOrderByDetectedAtDesc(String symbol);
    
    List<ArbitrageOpportunity> findByStatusOrderByDetectedAtDesc(String status);
    
    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.detectedAt >= :startDate ORDER BY a.spread DESC")
    List<ArbitrageOpportunity> findRecentOpportunities(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE' ORDER BY a.projectedDailyReturn DESC")
    List<ArbitrageOpportunity> findBestActiveOpportunities();
}
