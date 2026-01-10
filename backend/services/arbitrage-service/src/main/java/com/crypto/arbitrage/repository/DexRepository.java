package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DexRepository extends JpaRepository<Dex, Long> {
    
    Optional<Dex> findByName(String name);
    
    List<Dex> findByActiveTrue();
}
