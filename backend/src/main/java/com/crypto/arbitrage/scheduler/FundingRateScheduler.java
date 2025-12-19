package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.DexFetcher;
import com.crypto.arbitrage.service.ParadexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scheduler for fetching funding rates from all DEXes.
 * Uses the DexFetcher interface to dynamically iterate over all registered DEX
 * services.
 * Adding a new DEX only requires implementing DexFetcher - no changes needed
 * here.
 */
@Component
@Slf4j
public class FundingRateScheduler {

    private final List<DexFetcher> dexFetchers; // Auto-injected by Spring
    private final ParadexService paradexService; // Special case: per-symbol fetch
    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    public FundingRateScheduler(
            List<DexFetcher> dexFetchers,
            ParadexService paradexService,
            DexRepository dexRepository,
            DexMarketRepository dexMarketRepository) {
        this.dexFetchers = dexFetchers;
        this.paradexService = paradexService;
        this.dexRepository = dexRepository;
        this.dexMarketRepository = dexMarketRepository;

    }

    /**
     * Fetches funding rates every 10 seconds from all registered DEXes.
     */
    @Scheduled(fixedDelay = 10000)
    public void fetchFundingRatesLoop() {
        long startTime = System.currentTimeMillis();

        // 1. Fetch from all DexFetcher implementations (global fetch DEXes like
        // Hyperliquid, Lighter)
        List<CompletableFuture<Void>> globalFetchers = dexFetchers.stream()
                .filter(DexFetcher::isEnabled)
                .map(fetcher -> CompletableFuture.runAsync(() -> {
                    try {
                        fetcher.fetchFundingData();
                    } catch (Exception e) {
                        log.error("{}: Error fetching data", fetcher.getDexName(), e);
                    }
                }, executorService))
                .toList();

        // 2. Paradex: Special handling (per-symbol fetch)
        CompletableFuture<Void> paradexFuture = CompletableFuture.runAsync(() -> {
            try {
                dexRepository.findByName("Paradex").ifPresent(dex -> {
                    if (!dex.getActive())
                        return;

                    List<DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
                    if (markets.isEmpty())
                        return;

                    List<CompletableFuture<Void>> marketFutures = markets.stream()
                            .map(market -> CompletableFuture.runAsync(() -> {
                                try {
                                    paradexService.fetchAndSaveLatestFundingRate(market.getSymbol());
                                } catch (Exception e) {
                                    log.error("Paradex: Error fetching {}", market.getSymbol(), e);
                                }
                            }, executorService))
                            .toList();

                    CompletableFuture.allOf(marketFutures.toArray(new CompletableFuture[0])).join();
                });
            } catch (Exception e) {
                log.error("Error in Paradex fetch loop", e);
            }
        }, executorService);

        // Wait for all fetchers to complete
        CompletableFuture<Void> allGlobal = CompletableFuture.allOf(
                globalFetchers.toArray(new CompletableFuture[0]));
        CompletableFuture.allOf(allGlobal, paradexFuture).join();

        long duration = System.currentTimeMillis() - startTime;

    }

    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        // Initial setup handled by MarketSyncService
    }
}
