package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DexRepository extends JpaRepository<Dex, Long> {

    /**
     * Cached: every fetcher resolves its DEX by name once per market per cycle,
     * which produced hundreds of millions of scans on this 7-row table.
     * Dex has no lazy associations, so the cached instance is safe to reuse.
     */
    @Cacheable(value = "dexByName", key = "#name")
    Optional<Dex> findByName(String name);

    List<Dex> findByActiveTrue();
}
