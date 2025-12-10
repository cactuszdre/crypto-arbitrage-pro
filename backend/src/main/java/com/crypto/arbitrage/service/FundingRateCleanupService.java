package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for cleaning up old funding rate data.
 * Aggregates data older than 24 hours into daily averages, then deletes the raw data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRateCleanupService {

    private final FundingRateRepository fundingRateRepository;
    private final FundingRateDailyRepository fundingRateDailyRepository;
    private final TickerRepository tickerRepository;
    private final DexRepository dexRepository;

    /**
     * Helper method to convert numeric types from DB to BigDecimal.
     * PostgreSQL returns Double for AVG() functions.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    /**
     * Runs every day at 1:00 AM to clean up old funding rate data.
     * 1. Aggregates data older than 24 hours into daily averages
     * 2. Deletes the old raw data
     */
    @Scheduled(cron = "0 0 1 * * *") // Every day at 1:00 AM
    @Transactional
    public void cleanupOldFundingRates() {
        log.info("Starting funding rate cleanup job...");
        
        try {
            // Calculate cutoff time (24 hours ago)
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            LocalDate yesterday = cutoffTime.toLocalDate();
            
            // Step 1: Aggregate old rates
            List<Object[]> aggregatedData = fundingRateRepository.aggregateOldRates(cutoffTime);
            log.info("Found {} ticker/dex combinations to aggregate", aggregatedData.size());
            
            int savedCount = 0;
            for (Object[] row : aggregatedData) {
                try {
                    Integer tickerId = (Integer) row[0];
                    Long dexId = (Long) row[1];
                    BigDecimal avgRate = toBigDecimal(row[2]);
                    BigDecimal minRate = toBigDecimal(row[3]);
                    BigDecimal maxRate = toBigDecimal(row[4]);
                    BigDecimal avgRate8h = toBigDecimal(row[5]);
                    BigDecimal avgRateAnnual = toBigDecimal(row[6]);
                    Long sampleCount = (Long) row[7];
                    
                    // Get entities
                    Ticker ticker = tickerRepository.findById(tickerId).orElse(null);
                    Dex dex = dexRepository.findById(dexId).orElse(null);
                    
                    if (ticker == null || dex == null) {
                        log.warn("Skipping aggregation for tickerId={}, dexId={} - not found", tickerId, dexId);
                        continue;
                    }
                    
                    // Check if we already have an entry for this date
                    if (fundingRateDailyRepository.existsByTickerAndDexAndDate(ticker, dex, yesterday)) {
                        log.debug("Daily aggregate already exists for {} on {} - skipping", 
                                ticker.getNormalizedSymbol(), yesterday);
                        continue;
                    }
                    
                    // Create daily aggregate
                    FundingRateDaily daily = FundingRateDaily.builder()
                            .ticker(ticker)
                            .dex(dex)
                            .date(yesterday)
                            .avgRate(avgRate)
                            .minRate(minRate)
                            .maxRate(maxRate)
                            .avgRate8h(avgRate8h)
                            .avgRateAnnual(avgRateAnnual)
                            .sampleCount(sampleCount.intValue())
                            .build();
                    
                    fundingRateDailyRepository.save(daily);
                    savedCount++;
                    
                } catch (Exception e) {
                    log.error("Error aggregating row: {}", e.getMessage());
                }
            }
            
            log.info("Saved {} daily aggregates", savedCount);
            
            // Step 2: Delete old raw data
            int deletedCount = fundingRateRepository.deleteByTimestampBefore(cutoffTime);
            log.info("Deleted {} old funding rate records", deletedCount);
            
            log.info("Funding rate cleanup completed successfully. Aggregated: {}, Deleted: {}", 
                    savedCount, deletedCount);
            
        } catch (Exception e) {
            log.error("Error during funding rate cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for cleanup (useful for testing or admin actions)
     */
    @Transactional
    public void manualCleanup() {
        log.info("Manual cleanup triggered");
        cleanupOldFundingRates();
    }

    /**
     * Get statistics about current data
     */
    public String getCleanupStats() {
        long totalRates = fundingRateRepository.count();
        long dailyAggregates = fundingRateDailyRepository.count();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Object[]> oldRates = fundingRateRepository.aggregateOldRates(cutoff);
        
        return String.format(
            "Raw funding rates: %d, Daily aggregates: %d, Pending cleanup: %d ticker/dex combinations",
            totalRates, dailyAggregates, oldRates.size()
        );
    }
}
