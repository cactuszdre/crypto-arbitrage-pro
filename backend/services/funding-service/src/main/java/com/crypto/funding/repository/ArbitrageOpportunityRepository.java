package com.crypto.funding.repository;

import com.crypto.funding.model.ArbitrageOpportunity;
import com.crypto.funding.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArbitrageOpportunityRepository extends JpaRepository<ArbitrageOpportunity, Long> {
    
    List<ArbitrageOpportunity> findByTickerOrderByDetectedAtDesc(Ticker ticker);
    
    List<ArbitrageOpportunity> findByStatusOrderByDetectedAtDesc(String status);
    
    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.detectedAt >= :startDate ORDER BY a.spread DESC")
    List<ArbitrageOpportunity> findRecentOpportunities(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE' ORDER BY a.projectedDailyReturn DESC")
    List<ArbitrageOpportunity> findBestActiveOpportunities();
}

