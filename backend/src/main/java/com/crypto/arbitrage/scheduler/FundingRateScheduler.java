package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.service.HyperliquidService;
import com.crypto.arbitrage.service.LighterService;
import com.crypto.arbitrage.service.ParadexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundingRateScheduler {

    private final ParadexService paradexService;
    private final LighterService lighterService;
    private final HyperliquidService hyperliquidService;
    private final com.crypto.arbitrage.repository.DexRepository dexRepository;
    private final com.crypto.arbitrage.repository.DexMarketRepository dexMarketRepository;
    
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(20);

    /**
     * Récupère les funding rates toutes les 10 secondes
     */
    @Scheduled(fixedDelay = 10000) // 10 secondes APRÈS la fin de l'exécution précédente
    public void fetchFundingRatesLoop() {
        log.debug("=== Start periodic funding rate fetch ===");

        long startTime = System.currentTimeMillis();

        // 1. Paradex (Parallelized)
        java.util.concurrent.CompletableFuture<Void> paradexFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                dexRepository.findByName("Paradex").ifPresent(dex -> {
                    List<com.crypto.arbitrage.model.DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
                    if (markets.isEmpty()) {
                        log.debug("No active markets found for Paradex, skipping.");
                        return;
                    }

                    List<java.util.concurrent.CompletableFuture<Void>> futures = markets.stream()
                            .map(market -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    paradexService.fetchAndSaveLatestFundingRate(market.getSymbol());
                                } catch (Exception e) {
                                    log.error("Paradex: Error fetching {}", market.getSymbol(), e);
                                }
                            }, executorService))
                            .toList();

                    // Wait for all Paradex requests to complete
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                    log.info("Paradex sync completed for {} markets", markets.size());
                });
            } catch (Exception e) {
                log.error("Error in Paradex fetch loop", e);
            }
        }, executorService);

        // 2. Lighter (Global fetch)
        java.util.concurrent.CompletableFuture<Void> lighterFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                lighterService.fetchFundingData();
            } catch (Exception e) {
                log.error("Lighter: Error fetching data", e);
            }
        }, executorService);

        // 3. Hyperliquid (Global fetch)
        java.util.concurrent.CompletableFuture<Void> hyperliquidFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                hyperliquidService.fetchFundingData();
            } catch (Exception e) {
                log.error("Hyperliquid: Error fetching data", e);
            }
        }, executorService);

        // Wait for all DEXes to complete
        java.util.concurrent.CompletableFuture.allOf(paradexFuture, lighterFuture, hyperliquidFuture).join();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Total funding rate sync took {} ms", duration);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        // Initial fetch logic if needed, but the loop handles it.
        // MarketSyncService will run at 2s to populate the list.
    }
}
