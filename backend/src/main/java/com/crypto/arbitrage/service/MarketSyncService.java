package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSyncService {

    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;
    private final ParadexService paradexService;
    private final LighterService lighterService;
    private final HyperliquidService hyperliquidService;

    /**
     * Runs daily at midnight to refresh available markets for all DEXes.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void syncAllMarkets() {
        log.info("Starting daily market sync...");
        syncParadex();
        syncLighter();
        syncHyperliquid();
        log.info("Daily market sync completed.");
    }
    
    // Also run on startup after a delay
    @Scheduled(initialDelay = 2000, fixedDelay = Long.MAX_VALUE)
    public void syncOnStartup() {
        syncAllMarkets();
    }

    private void syncParadex() {
        try {
            Dex dex = dexRepository.findByName("Paradex").orElse(null);
            if (dex == null) return;

            // Paradex returns explicit market list
            List<String> symbols = paradexService.fetchAvailableSymbols(); // e.g. ["BTC-USD-PERP", ...]
            updateMarketsForDex(dex, symbols);
        } catch (Exception e) {
            log.error("Failed to sync Paradex markets", e);
        }
    }

    private void syncLighter() {
        try {
            Dex dex = dexRepository.findByName("Lighter").orElse(null);
            if (dex == null) return;

            // Lighter: we infer markets from the funding rates endpoint which returns all
            List<FundingRate> rates = lighterService.fetchFundingData();
            // Note: fetchFundingData saves rates, but here we just want the unique symbols
            List<String> symbols = rates.stream()
                    .map(FundingRate::getSymbol)
                    .distinct()
                    .collect(Collectors.toList());
            
            updateMarketsForDex(dex, symbols);
        } catch (Exception e) {
            log.error("Failed to sync Lighter markets", e);
        }
    }

    private void syncHyperliquid() {
        try {
            Dex dex = dexRepository.findByName("Hyperliquid").orElse(null);
            if (dex == null) return;

            // Hyperliquid: we infer markets from the funding rates endpoint
            List<FundingRate> rates = hyperliquidService.fetchFundingData();
            List<String> symbols = rates.stream()
                    .map(FundingRate::getSymbol)
                    .distinct()
                    .collect(Collectors.toList());
            
            updateMarketsForDex(dex, symbols);
        } catch (Exception e) {
            log.error("Failed to sync Hyperliquid markets", e);
        }
    }

    private void updateMarketsForDex(Dex dex, List<String> fetchedSymbols) {
        log.info("Updating markets for {}. Found {} symbols.", dex.getName(), fetchedSymbols.size());
        
        List<DexMarket> existingMarkets = dexMarketRepository.findByDexAndActiveTrue(dex);
        Set<String> existingSymbols = existingMarkets.stream()
                .map(DexMarket::getSymbol)
                .collect(Collectors.toSet());

        // Add new markets
        for (String symbol : fetchedSymbols) {
            if (!existingSymbols.contains(symbol)) {
                // Check if it exists but inactive
                DexMarket market = dexMarketRepository.findByDexAndSymbol(dex, symbol)
                        .orElse(DexMarket.builder()
                                .dex(dex)
                                .symbol(symbol)
                                .build());
                
                market.setActive(true);
                market.setUpdatedAt(LocalDateTime.now());
                dexMarketRepository.save(market);
                log.info("Activated new market for {}: {}", dex.getName(), symbol);
            }
        }

        // Deactivate old markets not found in fetch (Optional: might be risky if fetch fails partially)
        // For robustness, maybe we only deactivate if we are strictly sure. 
        // Let's hold off on auto-deactivating for now to prevent accidental data loss visibility.
    }
}
