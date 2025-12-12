package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.FundingRate5Min;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRate5MinRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service responsible for aggregating funding rate data into 5-minute buckets.
 * This pre-aggregated data is optimized for chart display.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRate5MinAggregationService {

    private final FundingRateRepository fundingRateRepository;
    private final FundingRate5MinRepository fundingRate5MinRepository;
    private final TickerRepository tickerRepository;
    private final DexRepository dexRepository;

    private static final int BUCKET_MINUTES = 5;
    private static final int RETENTION_HOURS = 48; // Keep 5-min data for 48 hours

    /**
     * Helper method to convert numeric types from DB to BigDecimal.
     * PostgreSQL returns Double for AVG() functions.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Float) return BigDecimal.valueOf((Float) value);
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    /**
     * Runs every 5 minutes to aggregate funding rate data into buckets.
     * Creates a new 5-minute aggregate from the data collected in the last 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes at :00, :05, :10, etc.
    @Transactional
    public void aggregateFundingRates() {
        log.info("Starting 5-minute funding rate aggregation...");

        try {
            // Calculate the bucket time window
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime bucketStart = calculateBucketTime(now.minusMinutes(BUCKET_MINUTES));
            LocalDateTime bucketEnd = bucketStart.plusMinutes(BUCKET_MINUTES);


            // Get aggregated data for this bucket
            List<Object[]> aggregatedData = fundingRateRepository.aggregateForBucket(bucketStart, bucketEnd);
            log.info("Found {} ticker/dex combinations to aggregate for bucket {}", aggregatedData.size(), bucketEnd);

            int savedCount = 0;
            int updatedCount = 0;

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

                    // Get open and close rates
                    BigDecimal openRate = getOpenRate(tickerId, dexId, bucketStart, bucketEnd);
                    BigDecimal closeRate = getCloseRate(tickerId, dexId, bucketStart, bucketEnd);

                    // Check if we already have an entry for this bucket (using bucketEnd as the label)
                    FundingRate5Min existing = fundingRate5MinRepository
                            .findByTickerAndDexAndBucketTime(ticker, dex, bucketEnd)
                            .orElse(null);

                    if (existing != null) {
                        // Update existing record
                        existing.setAvgRate(avgRate);
                        existing.setMinRate(minRate);
                        existing.setMaxRate(maxRate);
                        existing.setAvgRate8h(avgRate8h);
                        existing.setAvgRateAnnual(avgRateAnnual);
                        existing.setOpenRate(openRate);
                        existing.setCloseRate(closeRate);
                        existing.setSampleCount(sampleCount.intValue());
                        fundingRate5MinRepository.save(existing);
                        updatedCount++;
                    } else {
                        // Create new aggregate (bucketTime = end of period for intuitive display)
                        FundingRate5Min bucket = FundingRate5Min.builder()
                                .ticker(ticker)
                                .dex(dex)
                                .bucketTime(bucketEnd)
                                .avgRate(avgRate)
                                .minRate(minRate)
                                .maxRate(maxRate)
                                .avgRate8h(avgRate8h)
                                .avgRateAnnual(avgRateAnnual)
                                .openRate(openRate)
                                .closeRate(closeRate)
                                .sampleCount(sampleCount.intValue())
                                .build();

                        fundingRate5MinRepository.save(bucket);
                        savedCount++;
                    }

                } catch (Exception e) {
                    log.error("Error aggregating row: {}", e.getMessage());
                }
            }

            log.info("5-minute aggregation completed. New: {}, Updated: {}", savedCount, updatedCount);

        } catch (Exception e) {
            log.error("Error during 5-minute aggregation: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old 5-minute data (runs daily at 2:00 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOld5MinData() {
        log.info("Starting 5-minute data cleanup...");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(RETENTION_HOURS);
            int deletedCount = fundingRate5MinRepository.deleteByBucketTimeBefore(cutoffTime);
            log.info("Deleted {} old 5-minute records (older than {} hours)", deletedCount, RETENTION_HOURS);
        } catch (Exception e) {
            log.error("Error during 5-minute data cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the open rate (first rate in the bucket)
     */
    private BigDecimal getOpenRate(Integer tickerId, Long dexId, LocalDateTime startTime, LocalDateTime endTime) {
        List<FundingRate> firstRates = fundingRateRepository.findFirstInBucket(
                tickerId, dexId, startTime, endTime, PageRequest.of(0, 1));
        return firstRates.isEmpty() ? null : firstRates.get(0).getRate();
    }

    /**
     * Get the close rate (last rate in the bucket)
     */
    private BigDecimal getCloseRate(Integer tickerId, Long dexId, LocalDateTime startTime, LocalDateTime endTime) {
        List<FundingRate> lastRates = fundingRateRepository.findLastInBucket(
                tickerId, dexId, startTime, endTime, PageRequest.of(0, 1));
        return lastRates.isEmpty() ? null : lastRates.get(0).getRate();
    }

    /**
     * Calculate the start of a 5-minute bucket for a given time.
     * E.g., 10:07:32 -> 10:05:00
     */
    private LocalDateTime calculateBucketTime(LocalDateTime time) {
        int minute = time.getMinute();
        int bucketMinute = (minute / BUCKET_MINUTES) * BUCKET_MINUTES;
        return time.truncatedTo(ChronoUnit.HOURS).plusMinutes(bucketMinute);
    }

    /**
     * Manual trigger for aggregation (useful for testing or catching up)
     */
    @Transactional
    public void manualAggregate() {
        log.info("Manual 5-minute aggregation triggered");
        aggregateFundingRates();
    }

    /**
     * Backfill missing buckets for a time range
     */
    @Transactional
    public void backfillBuckets(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Backfilling 5-minute buckets from {} to {}", startTime, endTime);

        LocalDateTime currentBucket = calculateBucketTime(startTime);
        int bucketsCreated = 0;

        while (currentBucket.isBefore(endTime)) {
            LocalDateTime bucketEnd = currentBucket.plusMinutes(BUCKET_MINUTES);

            List<Object[]> aggregatedData = fundingRateRepository.aggregateForBucket(currentBucket, bucketEnd);

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

                    Ticker ticker = tickerRepository.findById(tickerId).orElse(null);
                    Dex dex = dexRepository.findById(dexId).orElse(null);

                    if (ticker == null || dex == null) continue;

                    // Skip if already exists (using bucketEnd as the label)
                    if (fundingRate5MinRepository.existsByTickerAndDexAndBucketTime(ticker, dex, bucketEnd)) {
                        continue;
                    }

                    BigDecimal openRate = getOpenRate(tickerId, dexId, currentBucket, bucketEnd);
                    BigDecimal closeRate = getCloseRate(tickerId, dexId, currentBucket, bucketEnd);

                    FundingRate5Min bucket = FundingRate5Min.builder()
                            .ticker(ticker)
                            .dex(dex)
                            .bucketTime(bucketEnd)
                            .avgRate(avgRate)
                            .minRate(minRate)
                            .maxRate(maxRate)
                            .avgRate8h(avgRate8h)
                            .avgRateAnnual(avgRateAnnual)
                            .openRate(openRate)
                            .closeRate(closeRate)
                            .sampleCount(sampleCount.intValue())
                            .build();

                    fundingRate5MinRepository.save(bucket);
                    bucketsCreated++;

                } catch (Exception e) {
                    log.error("Error backfilling bucket at {}: {}", currentBucket, e.getMessage());
                }
            }

            currentBucket = currentBucket.plusMinutes(BUCKET_MINUTES);
        }

        log.info("Backfill completed. Created {} buckets", bucketsCreated);
    }

    /**
     * Get statistics about 5-minute bucket data
     */
    public String getAggregationStats() {
        long totalBuckets = fundingRate5MinRepository.count();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        List<FundingRate5Min> recentBuckets = fundingRate5MinRepository.findAllSince(cutoff);

        return String.format(
                "Total 5-min buckets: %d, Buckets in last hour: %d",
                totalBuckets, recentBuckets.size()
        );
    }
}
