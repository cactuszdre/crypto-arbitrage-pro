package com.crypto.arbitrage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for clearing historical data caches before new data is generated.
 * Historical data is aggregated daily at 01:00, so we clear the cache at 00:55
 * to ensure users get fresh data after the aggregation completes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionService {

    private final CacheManager cacheManager;

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
