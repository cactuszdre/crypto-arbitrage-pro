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

    public MarketSyncService(
            DexRepository dexRepository,
            DexMarketRepository dexMarketRepository,
            TickerService tickerService,
            ParadexService paradexService,
            List<DexFetcher> dexFetchers) {
        this.dexRepository = dexRepository;
        this.dexMarketRepository = dexMarketRepository;
        this.tickerService = tickerService;
        this.paradexService = paradexService;
        this.fetchersByName = dexFetchers.stream()
                .collect(Collectors.toMap(DexFetcher::getDexName, Function.identity()));
        
        log.info("MarketSyncService initialized with {} DexFetcher implementations", fetchersByName.size());
    }

    /**
     * Runs daily at midnight to refresh available markets for all DEXes.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void syncAllMarkets() {
        log.info("Starting daily market sync...");
        
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
        
        log.info("Daily market sync completed for {} DEXes", activeDexes.size());
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
            log.info("Synced {} markets for {}", symbols.size(), dexName);
        }
    }
    
    // Also run on startup after a delay
    @Scheduled(initialDelay = 2000, fixedDelay = Long.MAX_VALUE)
    public void syncOnStartup() {
        syncAllMarkets();
    }

    private void updateMarketsForDex(Dex dex, List<String> fetchedSymbols) {
        List<DexMarket> existingMarkets = dexMarketRepository.findByDexAndActiveTrue(dex);
        Set<String> existingSymbols = existingMarkets.stream()
                .map(DexMarket::getSymbol)
                .collect(Collectors.toSet());

        // Add new markets
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
                log.info("Activated new market for {}: {}", dex.getName(), symbol);
            }
        }
    }
}
