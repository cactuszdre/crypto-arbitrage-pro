package com.crypto.arbitrage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for cache management:
 * - Evicting historical data caches AFTER new data is generated (01:02)
 * - Warming up caches after eviction completes (01:05)
 * 
 * IMPORTANT: The daily cleanup job runs at 01:00 and aggregates yesterday's
 * data.
 * We evict caches at 01:02 (after cleanup completes) to ensure the next request
 * gets the fresh data including the newly aggregated day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionService {

    private final CacheManager cacheManager;
    private final HistoricalArbitrageService historicalArbitrageService;

    /**
     * Clear historical data caches every day at 01:02.
     * This runs 2 minutes AFTER the daily data aggregation at 01:00,
     * ensuring the new data is ready before we evict the old cache.
     * 
     * Previously ran at 00:55 (before aggregation) which caused a race condition:
     * if a user accessed the API between 00:55 and 01:00, the cache would be
     * populated with stale data (missing the newest day) and kept for 24h.
     */
    @Scheduled(cron = "0 2 1 * * *") // Every day at 01:02
    public void evictHistoricalCaches() {

        evictCache("historicalArbitrage");
        evictCache("historicalRatesBySymbol");

    }

    /**
     * Warm up historical data caches every day at 01:05.
     * This runs after cache eviction at 01:02,
     * pre-populating both cache types so the first user gets instant data.
     */
    @Scheduled(cron = "0 5 1 * * *") // Every day at 01:05
    public void warmUpHistoricalCaches() {

        int[] periodsToWarm = { 1, 7, 30, 90, 180, 365 };

        for (int days : periodsToWarm) {
            try {
                // Warm up historicalRatesBySymbol cache
                historicalArbitrageService.getAllHistoricalRatesBySymbol(days);

                // Also warm up historicalArbitrage cache (was missing before!)
                historicalArbitrageService.getHistoricalOpportunities(days);
            } catch (Exception e) {
                log.error("Failed to warm cache for {} days: {}", days, e.getMessage());
            }
        }

    }

    /**
     * Manually evict and refresh all historical caches.
     * Useful for admin actions or when new data needs to be visible immediately.
     */
    public void refreshHistoricalCaches() {
        evictHistoricalCaches();
        warmUpHistoricalCaches();
    }

    private void evictCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        } else {
            log.warn("Cache '{}' not found", cacheName);
        }
    }
}
