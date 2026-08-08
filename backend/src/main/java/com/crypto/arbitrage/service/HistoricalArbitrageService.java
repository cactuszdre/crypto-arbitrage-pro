package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.DexRateDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageResponse;
import com.crypto.arbitrage.dto.HistoricalRatesResponse;
import com.crypto.arbitrage.dto.SymbolRatesDTO;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for calculating historical arbitrage opportunities from daily
 * aggregated data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HistoricalArbitrageService {

    private final FundingRateDailyRepository fundingRateDailyRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("MMM d");

    /**
     * Get historical arbitrage opportunities for a given time period.
     * Cached for 5 minutes since historical data doesn't change frequently.
     *
     * @param days Number of days to look back (1, 7, 30, 90, 180, 365)
     * @return Response containing opportunities and metadata about the actual data
     *         range
     */
    @Cacheable(value = "historicalArbitrage", key = "#days")
    public HistoricalArbitrageResponse getHistoricalOpportunities(int days) {
        LocalDate requestedStartDate = LocalDate.now().minusDays(days);

        // Get actual date range from database
        LocalDate actualStartDate = fundingRateDailyRepository.findMinDateSince(requestedStartDate);
        LocalDate actualEndDate = fundingRateDailyRepository.findMaxDate();
        int dataPointCount = fundingRateDailyRepository.countDistinctDatesSince(requestedStartDate);

        // Handle case where no data exists
        if (actualStartDate == null || actualEndDate == null || dataPointCount == 0) {
            return HistoricalArbitrageResponse.builder()
                    .opportunities(List.of())
                    .actualStartDate(null)
                    .actualEndDate(null)
                    .dataPointCount(0)
                    .periodLabel("No data available")
                    .requestedDays(days)
                    .build();
        }

        // Build human-readable period label
        String periodLabel = buildPeriodLabel(actualStartDate, actualEndDate, dataPointCount);

        List<HistoricalArbitrageDTO> opportunities = calculateOpportunities(
                loadAveragesBySymbol(requestedStartDate), actualStartDate, actualEndDate);

        return HistoricalArbitrageResponse.builder()
                .opportunities(opportunities)
                .actualStartDate(actualStartDate)
                .actualEndDate(actualEndDate)
                .dataPointCount(dataPointCount)
                .periodLabel(periodLabel)
                .requestedDays(days)
                .build();
    }

    /**
     * Build a human-readable period label based on the actual data range.
     */
    private String buildPeriodLabel(LocalDate startDate, LocalDate endDate, int dataPointCount) {
        if (startDate.equals(endDate)) {
            // Single day - typically "1 Day" selection showing yesterday
            if (endDate.equals(LocalDate.now().minusDays(1))) {
                return "Yesterday (" + endDate.format(DATE_FORMATTER) + ")";
            }
            return endDate.format(DATE_FORMATTER);
        }

        // Multiple days
        String dateRange;
        if (startDate.getYear() == endDate.getYear()) {
            // Same year - use shorter format for start date
            dateRange = startDate.format(DATE_FORMATTER_SHORT) + " - " + endDate.format(DATE_FORMATTER);
        } else {
            // Different years - show full dates
            dateRange = startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER);
        }

        return dateRange + " (" + dataPointCount + " day" + (dataPointCount > 1 ? "s" : "") + ")";
    }

    /** Average annual rate and sample count of one DEX over a symbol's common period. */
    private record DexAverage(String dexName, BigDecimal avgRate, int sampleCount) {
    }

    /**
     * Averages per symbol, already restricted to each symbol's common date range
     * by the database. Preserves insertion order so symbols stay alphabetical.
     */
    private Map<String, List<DexAverage>> loadAveragesBySymbol(LocalDate requestedStartDate) {
        Map<String, List<DexAverage>> bySymbol = new LinkedHashMap<>();
        for (Object[] row : fundingRateDailyRepository.aggregateRatesPerSymbolAndDexSince(requestedStartDate)) {
            String symbol = (String) row[0];
            String dexName = (String) row[1];
            BigDecimal avgRate = ((BigDecimal) row[2]).setScale(4, RoundingMode.HALF_UP);
            int sampleCount = ((Number) row[3]).intValue();
            bySymbol.computeIfAbsent(symbol, s -> new ArrayList<>())
                    .add(new DexAverage(dexName, avgRate, sampleCount));
        }
        return bySymbol;
    }

    /**
     * Calculate arbitrage opportunities from the per-symbol DEX averages.
     */
    private List<HistoricalArbitrageDTO> calculateOpportunities(
            Map<String, List<DexAverage>> averagesBySymbol,
            LocalDate actualStartDate,
            LocalDate actualEndDate) {

        List<HistoricalArbitrageDTO> opportunities = new ArrayList<>();

        for (Map.Entry<String, List<DexAverage>> entry : averagesBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<DexAverage> dexAverages = entry.getValue();

            // Need at least 2 DEXes for arbitrage
            if (dexAverages.size() < 2)
                continue;

            Map<String, BigDecimal> dexAvgRates = new HashMap<>();
            Map<String, Integer> dexSampleCounts = new HashMap<>();
            for (DexAverage avg : dexAverages) {
                dexAvgRates.put(avg.dexName(), avg.avgRate());
                dexSampleCounts.put(avg.dexName(), avg.sampleCount());
            }

            // Find best long (lowest rate) and best short (highest rate)
            String bestLongDex = null;
            String bestShortDex = null;
            BigDecimal lowestRate = null;
            BigDecimal highestRate = null;

            for (Map.Entry<String, BigDecimal> rateEntry : dexAvgRates.entrySet()) {
                String dexName = rateEntry.getKey();
                BigDecimal rate = rateEntry.getValue();

                if (lowestRate == null || rate.compareTo(lowestRate) < 0) {
                    lowestRate = rate;
                    bestLongDex = dexName;
                }
                if (highestRate == null || rate.compareTo(highestRate) > 0) {
                    highestRate = rate;
                    bestShortDex = dexName;
                }
            }

            // Skip if same DEX
            if (bestLongDex.equals(bestShortDex))
                continue;

            // Calculate spread (can be negative in historical data - this is accurate)
            BigDecimal spread = highestRate.subtract(lowestRate);

            // Daily return = spread / 365
            BigDecimal dailyReturn = spread.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);

            // Total sample count (min of both DEXes)
            int sampleCount = Math.min(
                    dexSampleCounts.getOrDefault(bestLongDex, 0),
                    dexSampleCounts.getOrDefault(bestShortDex, 0));

            HistoricalArbitrageDTO dto = HistoricalArbitrageDTO.builder()
                    .symbol(symbol)
                    .longDex(bestLongDex)
                    .shortDex(bestShortDex)
                    .longRate(lowestRate)
                    .shortRate(highestRate)
                    .spread(spread)
                    .projectedDailyReturn(dailyReturn)
                    .sampleCount(sampleCount)
                    .startDate(actualStartDate)
                    .endDate(actualEndDate)
                    .build();

            opportunities.add(dto);
        }

        // Sort by spread descending
        opportunities.sort((a, b) -> b.getSpread().compareTo(a.getSpread()));

        return opportunities;
    }

    /**
     * Get all historical rates grouped by symbol for frontend-side arbitrage
     * calculation.
     * Returns average rates per DEX for each symbol over the requested period.
     *
     * @param days Number of days to look back
     * @return Response containing symbols with their DEX rates and period metadata
     */
    @Cacheable(value = "historicalRatesBySymbol", key = "#days")
    public HistoricalRatesResponse getAllHistoricalRatesBySymbol(int days) {
        LocalDate requestedStartDate = LocalDate.now().minusDays(days);

        // Get actual date range from database
        LocalDate actualStartDate = fundingRateDailyRepository.findMinDateSince(requestedStartDate);
        LocalDate actualEndDate = fundingRateDailyRepository.findMaxDate();
        int dataPointCount = fundingRateDailyRepository.countDistinctDatesSince(requestedStartDate);

        // Handle case where no data exists
        if (actualStartDate == null || actualEndDate == null || dataPointCount == 0) {
            return HistoricalRatesResponse.builder()
                    .symbols(List.of())
                    .actualStartDate(null)
                    .actualEndDate(null)
                    .dataPointCount(0)
                    .periodLabel("No data available")
                    .requestedDays(days)
                    .build();
        }

        // Build human-readable period label
        String periodLabel = buildPeriodLabel(actualStartDate, actualEndDate, dataPointCount);

        List<SymbolRatesDTO> symbols = new ArrayList<>();
        for (Map.Entry<String, List<DexAverage>> entry : loadAveragesBySymbol(requestedStartDate).entrySet()) {
            List<DexRateDTO> dexRates = entry.getValue().stream()
                    .map(avg -> DexRateDTO.builder()
                            .dex(avg.dexName())
                            .rateAnnual(avg.avgRate())
                            .build())
                    .toList();

            symbols.add(SymbolRatesDTO.builder()
                    .symbol(entry.getKey())
                    .rates(dexRates)
                    .build());
        }

        // Sort by symbol name
        symbols.sort((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol()));

        return HistoricalRatesResponse.builder()
                .symbols(symbols)
                .actualStartDate(actualStartDate)
                .actualEndDate(actualEndDate)
                .dataPointCount(dataPointCount)
                .periodLabel(periodLabel)
                .requestedDays(days)
                .build();
    }
}
