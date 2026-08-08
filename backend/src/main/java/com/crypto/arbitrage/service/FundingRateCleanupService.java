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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for cleaning up old funding rate data.
 * Aggregates predicted raw samples older than 24 hours into daily averages, then deletes the raw data.
 * <p>
 * NOTE: Daily AVG of predicted samples is a monitoring fallback only.
 * Settled (paid) rates live in {@code funding_settlement} and must not be deleted by this job.
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
    private final TransactionTemplate transactionTemplate;

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
    public void cleanupOldFundingRates() {
        // Calculate yesterday's date range (from 00:00 to 23:59:59)
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay(); // 00:00:00 today

        // Split into separate transactions: a single one covering the aggregation of
        // thousands of rows plus a multi-million-row delete held a pooled connection
        // for minutes and starved web traffic.
        transactionTemplate.executeWithoutResult(status -> aggregateYesterday(yesterday));
        deleteRawBefore(endOfYesterday);
        pruneStaleLastFunding();
    }

    private void aggregateYesterday(LocalDate yesterday) {
        try {
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

            log.info("Aggregated {} daily funding rows for {}", savedCount, yesterday);
        } catch (Exception e) {
            log.error("Error during funding rate aggregation: {}", e.getMessage(), e);
        }
    }

    private static final int DELETE_BATCH_SIZE = 20_000;
    private static final int MAX_DELETE_BATCHES = 2_000;

    /**
     * Deletes expired raw samples in small committed batches so no single
     * transaction holds a connection long enough to starve the pool.
     */
    private void deleteRawBefore(LocalDateTime cutoff) {
        int total = 0;
        try {
            for (int batch = 0; batch < MAX_DELETE_BATCHES; batch++) {
                Integer deleted = transactionTemplate.execute(
                        status -> fundingRateRepository.deleteBatchByTimestampBefore(cutoff, DELETE_BATCH_SIZE));
                if (deleted == null || deleted == 0) {
                    break;
                }
                total += deleted;
            }
            log.info("Deleted {} raw funding rows older than {}", total, cutoff);
        } catch (Exception e) {
            log.error("Error deleting old funding rates after {} rows: {}", total, e.getMessage(), e);
        }
    }

    /**
     * Removes entries not updated in STALE_THRESHOLD_HOURS, meaning the DEX stopped
     * returning that pair (likely delisted).
     */
    private void pruneStaleLastFunding() {
        try {
            lastFundingService.deleteStaleEntries(STALE_THRESHOLD_HOURS);
        } catch (Exception e) {
            log.error("Error pruning stale last_funding entries: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for cleanup (useful for testing or admin actions).
     * Deliberately not transactional: cleanupOldFundingRates manages its own
     * per-step transaction boundaries.
     */
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
