package com.crypto.arbitrage.repository;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DexMarketRepository extends JpaRepository<DexMarket, Long> {
    List<DexMarket> findByDexAndActiveTrue(Dex dex);
    Optional<DexMarket> findByDexAndSymbol(Dex dex, String symbol);
    
    @Query("SELECT DISTINCT m.symbol FROM DexMarket m WHERE m.active = true ORDER BY m.symbol")
    List<String> findDistinctActiveSymbols();

    // Use native query to properly group by symbol and get one iconUrl per symbol
    @Query(value = "SELECT m.symbol as symbol, MAX(m.icon_url) as iconUrl FROM dex_market m WHERE m.active = true GROUP BY m.symbol ORDER BY m.symbol", nativeQuery = true)
    List<SymbolIconProjection> findDistinctSymbolsWithIconsNative();

    default List<com.crypto.arbitrage.dto.MarketSymbolDTO> findDistinctSymbolsWithIcons() {
        return findDistinctSymbolsWithIconsNative().stream()
                .map(p -> new com.crypto.arbitrage.dto.MarketSymbolDTO(p.getSymbol(), p.getIconUrl()))
                .toList();
    }
    
    // Projection interface for native query
    interface SymbolIconProjection {
        String getSymbol();
        String getIconUrl();
    }
}

