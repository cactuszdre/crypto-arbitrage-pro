package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TickerRepository extends JpaRepository<Ticker, Integer> {
    
    Optional<Ticker> findByNormalizedSymbol(String normalizedSymbol);
    
    Optional<Ticker> findByBaseSymbol(String baseSymbol);
}
