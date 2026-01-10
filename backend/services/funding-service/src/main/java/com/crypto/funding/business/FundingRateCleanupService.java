package com.crypto.funding.business;

import com.crypto.funding.model.Dex;
import com.crypto.funding.model.FundingRateDaily;
import com.crypto.funding.model.Ticker;
import com.crypto.funding.repository.DexRepository;
import com.crypto.funding.repository.FundingRateDailyRepository;
import com.crypto.funding.repository.FundingRateRepository;
import com.crypto.funding.repository.TickerRepository;
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
    private final LastFundingService lastFundingService;

    // Stale threshold: entries not updated in this many hours will be deleted
    private static final int STALE_THRESHOLD_HOURS = 24;

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
     * 1. Aggregates yesterday's data (from 00:00 to 23:59:59) into daily averages
     * 2. Deletes data older than today (keeps only today's data)
     */
    @Scheduled(cron = "0 0 1 * * *") // Every day at 1:00 AM
    @Transactional
    public void cleanupOldFundingRates() {
        try {
            // Calculate yesterday's date range (from 00:00 to 23:59:59)
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime startOfYesterday = yesterday.atStartOfDay();           // 00:00:00 yesterday
            LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay(); // 00:00:00 today
            
            List<Object[]> aggregatedData = fundingRateRepository.aggregateForBucket(startOfYesterday, endOfYesterday);
            
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
            
            
            // Step 2: Delete old raw data (everything before today 00:00)
            int deletedCount = fundingRateRepository.deleteByTimestampBefore(endOfYesterday);
            
            // Step 3: Clean up stale LastFunding entries (delisted pairs)
            // This removes entries that haven't been updated in STALE_THRESHOLD_HOURS,
            // which means the DEX stopped returning that pair (likely delisted)
            lastFundingService.deleteStaleEntries(STALE_THRESHOLD_HOURS);
            
        } catch (Exception e) {
            log.error("Error during funding rate cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for cleanup (useful for testing or admin actions)
     */
    @Transactional
    public void manualCleanup() {
        cleanupOldFundingRates();
    }

    /**
     * Get statistics about current data
     */
    public String getCleanupStats() {
        long totalRates = fundingRateRepository.count();
        long dailyAggregates = fundingRateDailyRepository.count();
        
        // Check what would be aggregated for yesterday
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfYesterday = yesterday.atStartOfDay();
        LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay();
        List<Object[]> pendingRates = fundingRateRepository.aggregateForBucket(startOfYesterday, endOfYesterday);
        
        return String.format(
            "Raw funding rates: %d, Daily aggregates: %d, Pending cleanup for %s: %d ticker/dex combinations",
            totalRates, dailyAggregates, yesterday, pendingRates.size()
        );
    }
}
