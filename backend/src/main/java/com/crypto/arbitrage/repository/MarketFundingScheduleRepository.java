package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.MarketFundingSchedule;
import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketFundingScheduleRepository extends JpaRepository<MarketFundingSchedule, Long> {

    Optional<MarketFundingSchedule> findByDexAndTicker(Dex dex, Ticker ticker);

    Optional<MarketFundingSchedule> findByDexIdAndTickerId(Long dexId, Integer tickerId);

    List<MarketFundingSchedule> findByDex(Dex dex);

    @Query("""
            SELECT s FROM MarketFundingSchedule s
            WHERE s.continuous = false
              AND s.nextFundingTime IS NOT NULL
              AND s.nextFundingTime >= :from
              AND s.nextFundingTime < :to
            """)
    List<MarketFundingSchedule> findDueForSettlementCapture(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
