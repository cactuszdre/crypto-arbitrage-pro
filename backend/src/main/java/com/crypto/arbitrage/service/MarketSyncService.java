package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for synchronizing available markets across all DEXes.
 * Uses DexFetcher interface to dynamically iterate over all registered DEX services.
 * Adding a new DEX only requires implementing DexFetcher - no changes needed here.
 */
@Service
@Slf4j
public class MarketSyncService {

    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;
    private final TickerService tickerService;
    private final ParadexService paradexService; // Special case: has fetchAvailableSymbols
    private final Map<String, DexFetcher> fetchersByName;
    private final LastFundingService lastFundingService;

    public MarketSyncService(
            DexRepository dexRepository,
            DexMarketRepository dexMarketRepository,
            TickerService tickerService,
            ParadexService paradexService,
            List<DexFetcher> dexFetchers,
            LastFundingService lastFundingService) {
        this.dexRepository = dexRepository;
        this.dexMarketRepository = dexMarketRepository;
        this.tickerService = tickerService;
        this.paradexService = paradexService;
        this.fetchersByName = dexFetchers.stream()
                .collect(Collectors.toMap(DexFetcher::getDexName, Function.identity()));
        this.lastFundingService = lastFundingService;
    }

    /**
     * Runs daily at midnight to refresh available markets for all DEXes.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void syncAllMarkets() {
        
        // Get all active DEXes from database
        List<Dex> activeDexes = dexRepository.findAll().stream()
                .filter(Dex::getActive)
                .toList();
        
        for (Dex dex : activeDexes) {
            try {
                syncDex(dex);
            } catch (Exception e) {
                log.error("Failed to sync {} markets", dex.getName(), e);
            }
        }
        
    }
    
    /**
     * Sync markets for a specific DEX.
     */
    private void syncDex(Dex dex) {
        String dexName = dex.getName();
        List<String> symbols;
        
        // Special case: Paradex has explicit symbol list endpoint
        if ("Paradex".equals(dexName)) {
            symbols = paradexService.fetchAvailableSymbols();
        } else {
            // For other DEXes, infer symbols from funding data
            DexFetcher fetcher = fetchersByName.get(dexName);
            if (fetcher == null) {
                log.warn("No DexFetcher found for DEX: {}", dexName);
                return;
            }
            
            List<FundingRate> rates = fetcher.fetchFundingData();
            symbols = rates.stream()
                    .map(FundingRate::getSymbol)
                    .distinct()
                    .collect(Collectors.toList());
        }
        
        if (!symbols.isEmpty()) {
            updateMarketsForDex(dex, symbols);
        }
    }
    
    // Also run on startup after a delay
    @Scheduled(initialDelay = 2000, fixedDelay = Long.MAX_VALUE)
    public void syncOnStartup() {
        syncAllMarkets();
    }

    /**
     * Manual trigger for market sync (useful for admin actions).
     * This will deactivate delisted markets and remove them from LastFunding.
     */
    @Transactional
    public void manualSync() {
        log.info("Manual market sync triggered");
        syncAllMarkets();
        log.info("Manual market sync completed");
    }

    /**
     * Reactivate all markets for a specific DEX.
     * Useful for recovery after accidental mass deactivation due to API errors.
     *
     * @param dexName Name of the DEX
     * @return Number of markets reactivated
     */
    @Transactional
    public int reactivateAllMarkets(String dexName) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        if (dexOpt.isEmpty()) {
            log.warn("DEX not found: {}", dexName);
            return 0;
        }

        Dex dex = dexOpt.get();
        List<DexMarket> inactiveMarkets = dexMarketRepository.findByDexAndActiveFalse(dex);
        
        int count = 0;
        for (DexMarket market : inactiveMarkets) {
            market.setActive(true);
            market.setUpdatedAt(LocalDateTime.now());
            dexMarketRepository.save(market);
            count++;
        }

        log.info("Reactivated {} markets for DEX: {}", count, dexName);
        return count;
    }


    private void updateMarketsForDex(Dex dex, List<String> fetchedSymbols) {
        List<DexMarket> existingMarkets = dexMarketRepository.findByDexAndActiveTrue(dex);
        Set<String> existingSymbols = existingMarkets.stream()
                .map(DexMarket::getSymbol)
                .collect(Collectors.toSet());
        Set<String> fetchedSymbolSet = Set.copyOf(fetchedSymbols);

        // SAFETY CHECK: Don't deactivate markets if the API returned an empty or suspiciously small list
        // This prevents accidental mass deactivation due to API errors/timeouts
        int fetchedCount = fetchedSymbols.size();
        int existingCount = existingMarkets.size();
        
        // If API returned nothing or significantly fewer symbols than we have, skip deactivation
        // (likely an API error, not actual delistings)
        boolean shouldDeactivate = true;
        if (fetchedCount == 0) {
            log.warn("API returned 0 symbols for {} - skipping deactivation to prevent data loss", dex.getName());
            shouldDeactivate = false;
        } else if (existingCount > 0 && fetchedCount < existingCount * 0.5) {
            // If we got less than 50% of what we had before, something is probably wrong
            log.warn("API returned only {} symbols for {} (had {}), skipping deactivation - possible API issue", 
                    fetchedCount, dex.getName(), existingCount);
            shouldDeactivate = false;
        }

        // 1. Deactivate markets that are no longer available on the DEX (e.g., delisted pairs)
        // Only if we have a valid response from the API
        if (shouldDeactivate) {
            for (DexMarket market : existingMarkets) {
                if (!fetchedSymbolSet.contains(market.getSymbol())) {
                    log.info("Deactivating delisted market: {} on {}", market.getSymbol(), dex.getName());
                    market.setActive(false);
                    market.setUpdatedAt(LocalDateTime.now());
                    dexMarketRepository.save(market);
                    
                    // Also remove from LastFunding table to stop it appearing in arbitrage
                    if (market.getTicker() != null) {
                        lastFundingService.deleteByTickerAndDex(market.getTicker(), dex);
                    }
                }
            }
        }

        // 2. Add new markets (always do this, even if deactivation was skipped)
        for (String symbol : fetchedSymbols) {
            if (!existingSymbols.contains(symbol)) {
                // Get or create ticker for this symbol
                Ticker ticker = tickerService.getOrCreate(symbol);
                
                // Check if market exists but inactive
                DexMarket market = dexMarketRepository.findByDexAndSymbol(dex, symbol)
                        .orElse(DexMarket.builder()
                                .dex(dex)
                                .symbol(symbol)
                                .build());
                
                market.setTicker(ticker);
                market.setActive(true);
                market.setUpdatedAt(LocalDateTime.now());
                dexMarketRepository.save(market);
            }
        }
    }
}

