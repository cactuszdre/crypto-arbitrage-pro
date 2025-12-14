package com.crypto.arbitrage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for cache management:
 * - Evicting historical data caches before new data is generated (00:55)
 * - Warming up caches after daily aggregation completes (01:05)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionService {

    private final CacheManager cacheManager;
    private final HistoricalArbitrageService historicalArbitrageService;

    /**
     * Clear historical data caches every day at 00:55.
     * This runs 5 minutes before the daily data aggregation at 01:00,
     * ensuring users get fresh data when the new aggregation is ready.
     */
    @Scheduled(cron = "0 55 0 * * *") // Every day at 00:55
    public void evictHistoricalCaches() {
        log.info("Evicting historical data caches at 00:55 (before daily aggregation)");
        
        evictCache("historicalArbitrage");
        evictCache("historicalRatesBySymbol");
        
        log.info("Historical caches evicted successfully");
    }

    /**
     * Warm up historical data caches every day at 01:05.
     * This runs after the daily data aggregation at 01:00,
     * pre-populating caches so the first user gets instant data.
     */
    @Scheduled(cron = "0 5 1 * * *") // Every day at 01:05
    public void warmUpHistoricalCaches() {
        log.info("Warming up historical data caches at 01:05 (after daily aggregation)");
        
        int[] periodsToWarm = {1, 7, 30, 90, 180, 365};
        
        for (int days : periodsToWarm) {
            try {
                // This call will populate the cache due to @Cacheable
                historicalArbitrageService.getAllHistoricalRatesBySymbol(days);
                log.info("Cache warmed for {} days historical data", days);
            } catch (Exception e) {
                log.error("Failed to warm cache for {} days: {}", days, e.getMessage());
            }
        }
        
        log.info("Historical cache warming completed");
    }

    private void evictCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Cache '{}' cleared", cacheName);
        } else {
            log.warn("Cache '{}' not found", cacheName);
        }
    }
}
