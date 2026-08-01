package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundingSettlementRepository extends JpaRepository<FundingSettlement, Long> {

    Optional<FundingSettlement> findByDexAndTickerAndSettlementTime(Dex dex, Ticker ticker, LocalDateTime settlementTime);

    boolean existsByDexIdAndTickerIdAndSettlementTime(Long dexId, Integer tickerId, LocalDateTime settlementTime);

    @Query("""
            SELECT s FROM FundingSettlement s
            WHERE s.ticker = :ticker AND s.settlementTime >= :start AND s.settlementTime < :end
            ORDER BY s.settlementTime ASC
            """)
    List<FundingSettlement> findByTickerBetween(
            @Param("ticker") Ticker ticker,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT s FROM FundingSettlement s
            WHERE s.ticker = :ticker AND s.dex = :dex
              AND s.settlementTime >= :start AND s.settlementTime < :end
            ORDER BY s.settlementTime ASC
            """)
    List<FundingSettlement> findByTickerAndDexBetween(
            @Param("ticker") Ticker ticker,
            @Param("dex") Dex dex,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT s FROM FundingSettlement s
            WHERE s.dex = :dex AND s.settlementTime >= :start AND s.settlementTime < :end
            ORDER BY s.settlementTime ASC
            """)
    List<FundingSettlement> findByDexBetween(
            @Param("dex") Dex dex,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT COUNT(*) FROM funding_settlement
            WHERE settlement_time >= :since
            """, nativeQuery = true)
    long countSince(@Param("since") LocalDateTime since);
}
