package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.FundingRateChartDTO;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate5Min;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRate5MinRepository;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for retrieving pre-aggregated chart data.
 * Provides optimized queries for real-time chart display.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRateChartService {

    private final FundingRate5MinRepository fundingRate5MinRepository;
    private final FundingRateDailyRepository fundingRateDailyRepository;
    private final TickerRepository tickerRepository;
    private final DexRepository dexRepository;

    /**
     * Get chart data for a specific symbol and DEX
     *
     * @param symbol The normalized symbol (e.g., "BTC")
     * @param dexName The DEX name (e.g., "Paradex")
     * @param hours Number of hours of data to retrieve
     * @return List of chart data points
     */
    public List<FundingRateChartDTO> getChartData(String symbol, String dexName, int hours) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        Dex dex = dexRepository.findByName(dexName).orElse(null);

        if (ticker == null || dex == null) {
            log.warn("Ticker or DEX not found: symbol={}, dex={}", symbol, dexName);
            return Collections.emptyList();
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<FundingRate5Min> data = fundingRate5MinRepository.findChartData(ticker, dex, startTime, endTime);

        return data.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get chart data for a symbol across all DEXs
     *
     * @param symbol The normalized symbol
     * @param hours Number of hours of data
     * @return List of chart data points from all DEXs
     */
    public List<FundingRateChartDTO> getChartDataBySymbol(String symbol, int hours) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);

        if (ticker == null) {
            log.warn("Ticker not found: {}", symbol);
            return Collections.emptyList();
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<FundingRate5Min> data = fundingRate5MinRepository.findChartDataByTicker(ticker, startTime, endTime);

        return data.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get chart data for all symbols on a specific DEX
     *
     * @param dexName The DEX name
     * @param hours Number of hours of data
     * @return List of chart data points
     */
    public List<FundingRateChartDTO> getChartDataByDex(String dexName, int hours) {
        Dex dex = dexRepository.findByName(dexName).orElse(null);

        if (dex == null) {
            log.warn("DEX not found: {}", dexName);
            return Collections.emptyList();
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<FundingRate5Min> data = fundingRate5MinRepository.findChartDataByDex(dex, startTime, endTime);

        return data.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get the latest N data points for a symbol/DEX combination
     *
     * @param symbol The symbol
     * @param dexName The DEX name
     * @param count Number of data points
     * @return Latest data points
     */
    public List<FundingRateChartDTO> getLatestDataPoints(String symbol, String dexName, int count) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        Dex dex = dexRepository.findByName(dexName).orElse(null);

        if (ticker == null || dex == null) {
            return Collections.emptyList();
        }

        List<FundingRate5Min> data = fundingRate5MinRepository.findLatestBuckets(
                ticker, dex, PageRequest.of(0, count));

        // Reverse to get chronological order
        Collections.reverse(data);

        return data.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get chart data grouped by DEX for comparison charts
     *
     * @param symbol The symbol to compare
     * @param hours Number of hours
     * @return Map of DEX name to chart data
     */
    public Map<String, List<FundingRateChartDTO>> getComparisonData(String symbol, int hours) {
        List<FundingRateChartDTO> allData = getChartDataBySymbol(symbol, hours);

        return allData.stream()
                .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));
    }

    /**
     * Get daily chart data grouped by DEX for historical comparison charts (7D, 30D)
     * Uses the funding_rate_daily table for aggregated daily data
     *
     * @param symbol The symbol to compare
     * @param days Number of days
     * @return Map of DEX name to chart data
     */
    public Map<String, List<FundingRateChartDTO>> getDailyComparisonData(String symbol, int days) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        
        if (ticker == null) {
            log.warn("Ticker not found: {}", symbol);
            return Collections.emptyMap();
        }
        
        LocalDate startDate = LocalDate.now().minusDays(days);
        
        // Get all daily data for this ticker since startDate
        List<FundingRateDaily> dailyData = fundingRateDailyRepository.findByTickerSince(ticker, startDate);
        
        // Convert to DTOs and group by DEX
        return dailyData.stream()
                .map(this::toDailyDTO)
                .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));
    }
    
    /**
     * Convert FundingRateDaily entity to chart DTO
     */
    private FundingRateChartDTO toDailyDTO(FundingRateDaily entity) {
        return FundingRateChartDTO.builder()
                .id(entity.getId())
                .symbol(entity.getSymbol())
                .dexName(entity.getDex() != null ? entity.getDex().getName() : null)
                .bucketTime(entity.getDate().atStartOfDay())
                .avgRate(entity.getAvgRate())
                .avgRate8h(entity.getAvgRate8h())
                .avgRateAnnual(entity.getAvgRateAnnual())
                .minRate(entity.getMinRate())
                .maxRate(entity.getMaxRate())
                .sampleCount(entity.getSampleCount())
                .build();
    }

    /**
     * Get recent statistics summary for a symbol
     *
     * @param symbol The symbol
     * @param hours Number of hours for calculation
     * @return Statistics summary
     */
    public ChartStatsSummary getStatsSummary(String symbol, int hours) {
        List<FundingRateChartDTO> data = getChartDataBySymbol(symbol, hours);

        if (data.isEmpty()) {
            return ChartStatsSummary.empty();
        }

        var rates = data.stream()
                .map(FundingRateChartDTO::getAvgRate)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        if (rates.isEmpty()) {
            return ChartStatsSummary.empty();
        }

        var min = rates.stream().min(java.math.BigDecimal::compareTo).orElse(null);
        var max = rates.stream().max(java.math.BigDecimal::compareTo).orElse(null);
        var avg = rates.stream()
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .divide(java.math.BigDecimal.valueOf(rates.size()), 12, java.math.RoundingMode.HALF_UP);

        return ChartStatsSummary.builder()
                .symbol(symbol)
                .hours(hours)
                .dataPointCount(data.size())
                .minRate(min)
                .maxRate(max)
                .avgRate(avg)
                .firstRate(data.get(0).getAvgRate())
                .lastRate(data.get(data.size() - 1).getAvgRate())
                .build();
    }

    private FundingRateChartDTO toDTO(FundingRate5Min entity) {
        return FundingRateChartDTO.builder()
                .id(entity.getId())
                .symbol(entity.getSymbol())
                .dexName(entity.getDexName())
                .bucketTime(entity.getBucketTime())
                .avgRate(entity.getAvgRate())
                .avgRate8h(entity.getAvgRate8h())
                .avgRateAnnual(entity.getAvgRateAnnual())
                .openRate(entity.getOpenRate())
                .closeRate(entity.getCloseRate())
                .minRate(entity.getMinRate())
                .maxRate(entity.getMaxRate())
                .sampleCount(entity.getSampleCount())
                .build();
    }

    /**
     * Summary statistics for chart data
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChartStatsSummary {
        private String symbol;
        private int hours;
        private int dataPointCount;
        private java.math.BigDecimal minRate;
        private java.math.BigDecimal maxRate;
        private java.math.BigDecimal avgRate;
        private java.math.BigDecimal firstRate;
        private java.math.BigDecimal lastRate;

        public static ChartStatsSummary empty() {
            return ChartStatsSummary.builder()
                    .dataPointCount(0)
                    .build();
        }
    }
}
