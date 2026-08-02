package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.DexFetcher;
import com.crypto.arbitrage.service.OndoPerpsService;
import com.crypto.arbitrage.service.ParadexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Scheduler for fetching funding rates from all DEXes.
 * Uses the DexFetcher interface to dynamically iterate over all registered DEX
 * services. Paradex and Ondo Perps are special-cased (per-symbol fetch).
 */
@Component
@Slf4j
public class FundingRateScheduler {

    private final List<DexFetcher> dexFetchers; // Auto-injected by Spring
    private final ParadexService paradexService; // Special case: per-symbol fetch
    private final OndoPerpsService ondoPerpsService; // Special case: per-symbol fetch
    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    public FundingRateScheduler(
            List<DexFetcher> dexFetchers,
            ParadexService paradexService,
            OndoPerpsService ondoPerpsService,
            DexRepository dexRepository,
            DexMarketRepository dexMarketRepository) {
        this.dexFetchers = dexFetchers;
        this.paradexService = paradexService;
        this.ondoPerpsService = ondoPerpsService;
        this.dexRepository = dexRepository;
        this.dexMarketRepository = dexMarketRepository;

    }

    /**
     * Fetches funding rates from all registered DEXes.
     * 30s delay keeps the 1-vCPU droplet responsive for API traffic;
     * cycles often take 7–15s with Paradex/Ondo per-market fetches.
     */
    @Scheduled(fixedDelay = 30000)
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

        // 2. Per-symbol DEXes (Paradex, Ondo Perps)
        CompletableFuture<Void> paradexFuture = fetchPerSymbolDex(
                "Paradex",
                market -> paradexService.fetchAndSaveLatestFundingRate(market.getSymbol()));
        CompletableFuture<Void> ondoFuture = fetchPerSymbolDex(
                "Ondo Perps",
                market -> ondoPerpsService.fetchAndSaveLatestFundingRate(market.getSymbol()));

        // Wait for all fetchers to complete with a timeout
        CompletableFuture<Void> allGlobal = CompletableFuture.allOf(
                globalFetchers.toArray(new CompletableFuture[0]));
        
        try {
            CompletableFuture.allOf(allGlobal, paradexFuture, ondoFuture)
                    .get(45, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Funding rate fetch cycle timed out after 45s. Some fetches may be incomplete.");
            // We don't cancel explicitly here because we want them to finish if they can, 
            // but we unblock the scheduler thread.
            // Ideally, we should cancel them if they are genuinely stuck.
            allGlobal.cancel(true);
            paradexFuture.cancel(true);
            ondoFuture.cancel(true);
        } catch (Exception e) {
            log.error("Error in funding rate fetch loop", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        if (duration > 5000) {
             log.info("Funding rate fetch took {} ms", duration);
        }
    }

    private CompletableFuture<Void> fetchPerSymbolDex(String dexName, Consumer<DexMarket> fetchOne) {
        return CompletableFuture.runAsync(() -> {
            try {
                dexRepository.findByName(dexName).ifPresent(dex -> {
                    if (!dex.getActive())
                        return;

                    List<DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
                    if (markets.isEmpty())
                        return;

                    List<CompletableFuture<Void>> marketFutures = markets.stream()
                            .map(market -> CompletableFuture.runAsync(() -> {
                                try {
                                    fetchOne.accept(market);
                                } catch (Exception e) {
                                    log.error("{}: Error fetching {}", dexName, market.getSymbol(), e);
                                }
                            }, executorService))
                            .toList();

                    CompletableFuture.allOf(marketFutures.toArray(new CompletableFuture[0])).join();
                });
            } catch (Exception e) {
                log.error("Error in {} fetch loop", dexName, e);
            }
        }, executorService);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        // Initial setup handled by MarketSyncService
    }
}
