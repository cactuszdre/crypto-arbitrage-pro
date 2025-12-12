package com.crypto.arbitrage.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine for high-performance in-memory caching.
 * Caches arbitrage opportunities and funding rates to reduce load on database.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure multiple Caffeine caches with different TTLs:
     * - arbitrageOpportunities: 10 seconds (live data)
     * - latestFundingRates: 10 seconds (live data)
     * - historicalArbitrage: 5 minutes (historical data changes rarely)
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // Live data caches - 10 seconds TTL
        CaffeineCache arbitrageCache = new CaffeineCache("arbitrageOpportunities",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build());
        
        CaffeineCache fundingRatesCache = new CaffeineCache("latestFundingRates",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build());
        
        // Raw rates cache for frontend-side arbitrage calculation - 10 seconds TTL
        CaffeineCache allRatesCache = new CaffeineCache("allRatesBySymbol",
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build());
        
        // Historical data cache - 5 minutes TTL (keyed by days parameter)
        CaffeineCache historicalCache = new CaffeineCache("historicalArbitrage",
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(20)  // Cache different day ranges (1, 7, 30, 90, 180, 365)
                .recordStats()
                .build());
        
        // Historical raw rates cache for frontend-side calculation - 5 minutes TTL
        CaffeineCache historicalRatesCache = new CaffeineCache("historicalRatesBySymbol",
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(20)
                .recordStats()
                .build());
        
        cacheManager.setCaches(Arrays.asList(
            arbitrageCache, 
            fundingRatesCache, 
            allRatesCache,
            historicalCache,
            historicalRatesCache
        ));
        
        return cacheManager;
    }
}
