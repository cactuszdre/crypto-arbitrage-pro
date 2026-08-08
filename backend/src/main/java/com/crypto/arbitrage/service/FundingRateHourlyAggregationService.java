package com.crypto.arbitrage.service;

import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.FundingRateHourly;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateHourlyRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service responsible for aggregating funding rate data into 1-hour buckets.
 * This pre-aggregated data is used for 1D, 3D, and 7D chart display.
 * Aggregates directly from raw 10-second funding rate data for maximum precision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRateHourlyAggregationService {

    private final FundingRateRepository fundingRateRepository;
    private final FundingRateHourlyRepository fundingRateHourlyRepository;
    private final TickerRepository tickerRepository;
    private final DexRepository dexRepository;

    private static final int BUCKET_MINUTES = 60; // 1 hour
    private static final int RETENTION_DAYS = 7; // Keep hourly data for 7 days

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
     * Runs every hour at :05 to aggregate funding rate data into hourly buckets.
     * Creates a new 1-hour aggregate from the raw data collected in the last hour.
     * Runs at :05 to ensure all data from the previous hour is available.
     */
    @Scheduled(cron = "0 5 * * * *") // Every hour at :05 (e.g., 10:05, 11:05, 12:05)
    @Transactional
    public void aggregateHourlyFundingRates() {

        try {
            // Calculate the bucket time window (previous hour)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime bucketEnd = now.truncatedTo(ChronoUnit.HOURS); // Current hour start
            LocalDateTime bucketStart = bucketEnd.minusHours(1); // Previous hour start


            // Get aggregated data for this bucket from raw 10-second data
            List<Object[]> aggregatedData = fundingRateRepository.aggregateForBucket(bucketStart, bucketEnd);

            // Preload entities and existing buckets; resolving them per row issued
            // thousands of extra queries inside one transaction.
            Set<Integer> tickerIds = aggregatedData.stream().map(r -> (Integer) r[0]).collect(Collectors.toSet());
            Set<Long> dexIds = aggregatedData.stream().map(r -> (Long) r[1]).collect(Collectors.toSet());
            Map<Integer, Ticker> tickersById = tickerRepository.findAllById(tickerIds).stream()
                    .collect(Collectors.toMap(Ticker::getId, Function.identity()));
            Map<Long, Dex> dexesById = dexRepository.findAllById(dexIds).stream()
                    .collect(Collectors.toMap(Dex::getId, Function.identity()));
            Map<String, FundingRateHourly> existingByKey = fundingRateHourlyRepository
                    .findByBucketTime(bucketEnd).stream()
                    .collect(Collectors.toMap(
                            b -> b.getTicker().getId() + ":" + b.getDex().getId(),
                            Function.identity(),
                            (a, b) -> a));
            Map<String, BigDecimal> openRates = FundingRate5MinAggregationService.toRateMap(
                    fundingRateRepository.findOpenRatesInBucket(bucketStart, bucketEnd));
            Map<String, BigDecimal> closeRates = FundingRate5MinAggregationService.toRateMap(
                    fundingRateRepository.findCloseRatesInBucket(bucketStart, bucketEnd));

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

                    Ticker ticker = tickersById.get(tickerId);
                    Dex dex = dexesById.get(dexId);

                    if (ticker == null || dex == null) {
                        log.warn("Skipping hourly aggregation for tickerId={}, dexId={} - not found", tickerId, dexId);
                        continue;
                    }

                    BigDecimal openRate = openRates.get(tickerId + ":" + dexId);
                    BigDecimal closeRate = closeRates.get(tickerId + ":" + dexId);

                    FundingRateHourly existing = existingByKey.get(tickerId + ":" + dexId);

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
                        fundingRateHourlyRepository.save(existing);
                        updatedCount++;
                    } else {
                        // Create new hourly aggregate (bucketTime = end of period for intuitive display)
                        FundingRateHourly bucket = FundingRateHourly.builder()
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

                        fundingRateHourlyRepository.save(bucket);
                        savedCount++;
                    }

                } catch (Exception e) {
                    log.error("Error aggregating hourly row: {}", e.getMessage());
                }
            }


        } catch (Exception e) {
            log.error("Error during hourly aggregation: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old hourly data (runs daily at 3:00 AM)
     * Keeps data for RETENTION_DAYS (7 days by default)
     */
    @Scheduled(cron = "0 0 3 * * *") // Every day at 3:00 AM
    @Transactional
    public void cleanupOldHourlyData() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int deletedCount = fundingRateHourlyRepository.deleteByBucketTimeBefore(cutoffTime);
        } catch (Exception e) {
            log.error("Error during hourly data cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the open rate (first rate in the bucket) from raw data
     */
    private BigDecimal getOpenRate(Integer tickerId, Long dexId, LocalDateTime startTime, LocalDateTime endTime) {
        List<FundingRate> firstRates = fundingRateRepository.findFirstInBucket(
                tickerId, dexId, startTime, endTime, PageRequest.of(0, 1));
        return firstRates.isEmpty() ? null : firstRates.get(0).getRate();
    }

    /**
     * Get the close rate (last rate in the bucket) from raw data
     */
    private BigDecimal getCloseRate(Integer tickerId, Long dexId, LocalDateTime startTime, LocalDateTime endTime) {
        List<FundingRate> lastRates = fundingRateRepository.findLastInBucket(
                tickerId, dexId, startTime, endTime, PageRequest.of(0, 1));
        return lastRates.isEmpty() ? null : lastRates.get(0).getRate();
    }

    /**
     * Manual trigger for aggregation (useful for testing or catching up)
     */
    @Transactional
    public void manualAggregate() {
        aggregateHourlyFundingRates();
    }

    /**
     * Backfill missing hourly buckets for a time range.
     * Useful for populating historical data.
     */
    @Transactional
    public void backfillBuckets(LocalDateTime startTime, LocalDateTime endTime) {

        LocalDateTime currentBucket = startTime.truncatedTo(ChronoUnit.HOURS);
        int bucketsCreated = 0;

        while (currentBucket.isBefore(endTime)) {
            LocalDateTime bucketEnd = currentBucket.plusHours(1);

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

                    // Skip if already exists
                    if (fundingRateHourlyRepository.existsByTickerAndDexAndBucketTime(ticker, dex, bucketEnd)) {
                        continue;
                    }

                    BigDecimal openRate = getOpenRate(tickerId, dexId, currentBucket, bucketEnd);
                    BigDecimal closeRate = getCloseRate(tickerId, dexId, currentBucket, bucketEnd);

                    FundingRateHourly bucket = FundingRateHourly.builder()
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

                    fundingRateHourlyRepository.save(bucket);
                    bucketsCreated++;

                } catch (Exception e) {
                    log.error("Error backfilling hourly bucket at {}: {}", currentBucket, e.getMessage());
                }
            }

            currentBucket = currentBucket.plusHours(1);
        }

    }

    /**
     * Get statistics about hourly bucket data
     */
    public String getAggregationStats() {
        long totalBuckets = fundingRateHourlyRepository.count();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<FundingRateHourly> recentBuckets = fundingRateHourlyRepository.findAllSince(cutoff);

        return String.format(
                "Total hourly buckets: %d, Buckets in last 24 hours: %d",
                totalBuckets, recentBuckets.size()
        );
    }
}
