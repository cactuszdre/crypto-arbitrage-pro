package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.DexRateDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageResponse;
import com.crypto.arbitrage.dto.HistoricalRatesResponse;
import com.crypto.arbitrage.dto.SymbolRatesDTO;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating historical arbitrage opportunities from daily aggregated data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalArbitrageService {

    private final FundingRateDailyRepository fundingRateDailyRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_SHORT = DateTimeFormatter.ofPattern("MMM d");

    /**
     * Get historical arbitrage opportunities for a given time period.
     * Cached for 5 minutes since historical data doesn't change frequently.
     *
     * @param days Number of days to look back (1, 7, 30, 90, 180, 365)
     * @return Response containing opportunities and metadata about the actual data range
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
        
        
        // Get all daily data for the period
        List<FundingRateDaily> allDailyData = fundingRateDailyRepository.findAllSince(requestedStartDate);
        
        // Calculate opportunities
        List<HistoricalArbitrageDTO> opportunities = calculateOpportunities(allDailyData, actualStartDate, actualEndDate);
        
        
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
    
    /**
     * Calculate arbitrage opportunities from the daily data.
     * Uses common date range per symbol to ensure fair comparisons between DEXes.
     */
    private List<HistoricalArbitrageDTO> calculateOpportunities(
            List<FundingRateDaily> allDailyData,
            LocalDate actualStartDate,
            LocalDate actualEndDate) {
        
        if (allDailyData.isEmpty()) {
            return List.of();
        }
        
        // Group by ticker
        Map<Integer, List<FundingRateDaily>> dataByTicker = allDailyData.stream()
                .collect(Collectors.groupingBy(d -> d.getTicker().getId()));
        
        List<HistoricalArbitrageDTO> opportunities = new ArrayList<>();
        
        for (Map.Entry<Integer, List<FundingRateDaily>> entry : dataByTicker.entrySet()) {
            List<FundingRateDaily> tickerData = entry.getValue();
            
            if (tickerData.isEmpty()) continue;
            
            Ticker ticker = tickerData.get(0).getTicker();
            String symbol = ticker.getNormalizedSymbol();
            
            // Group by DEX
            Map<Long, List<FundingRateDaily>> dataByDex = tickerData.stream()
                    .collect(Collectors.groupingBy(d -> d.getDex().getId()));
            
            // Need at least 2 DEXes for arbitrage
            if (dataByDex.size() < 2) continue;
            
            // ========== COMMON DATE RANGE CALCULATION ==========
            // Find the common start date: the most recent "minimum date" across all DEXes
            // This ensures we only compare data where ALL DEXes have entries
            LocalDate commonStartDate = dataByDex.values().stream()
                    .map(list -> list.stream()
                            .map(FundingRateDaily::getDate)
                            .min(LocalDate::compareTo)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)  // Take the most recent among the minimums
                    .orElse(actualStartDate);
            
            // Calculate average annual rate for each DEX using only common period data
            Map<String, BigDecimal> dexAvgRates = new HashMap<>();
            Map<String, Integer> dexSampleCounts = new HashMap<>();
            final LocalDate finalCommonStartDate = commonStartDate;
            
            for (Map.Entry<Long, List<FundingRateDaily>> dexEntry : dataByDex.entrySet()) {
                List<FundingRateDaily> dexData = dexEntry.getValue();
                String dexName = dexData.get(0).getDex().getName();
                
                // Filter to only include data from common start date onwards
                List<FundingRateDaily> filteredDexData = dexData.stream()
                        .filter(d -> !d.getDate().isBefore(finalCommonStartDate))
                        .toList();
                
                if (filteredDexData.isEmpty()) continue;
                
                // Calculate average of avgRateAnnual
                BigDecimal avgRate = filteredDexData.stream()
                        .map(FundingRateDaily::getAvgRateAnnual)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(filteredDexData.size()), 4, RoundingMode.HALF_UP);
                
                dexAvgRates.put(dexName, avgRate);
                dexSampleCounts.put(dexName, filteredDexData.size());
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
            if (bestLongDex.equals(bestShortDex)) continue;
            
            // Calculate spread (can be negative in historical data - this is accurate)
            BigDecimal spread = highestRate.subtract(lowestRate);
            
            // Daily return = spread / 365
            BigDecimal dailyReturn = spread.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);
            
            // Total sample count (min of both DEXes)
            int sampleCount = Math.min(
                    dexSampleCounts.getOrDefault(bestLongDex, 0),
                    dexSampleCounts.getOrDefault(bestShortDex, 0)
            );
            
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
     * Get all historical rates grouped by symbol for frontend-side arbitrage calculation.
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
        
        // Get all daily data for the period
        List<FundingRateDaily> allDailyData = fundingRateDailyRepository.findAllSince(requestedStartDate);
        
        // Group by ticker
        Map<Integer, List<FundingRateDaily>> dataByTicker = allDailyData.stream()
                .collect(Collectors.groupingBy(d -> d.getTicker().getId()));
        
        List<SymbolRatesDTO> symbols = new ArrayList<>();
        
        for (Map.Entry<Integer, List<FundingRateDaily>> entry : dataByTicker.entrySet()) {
            List<FundingRateDaily> tickerData = entry.getValue();
            
            if (tickerData.isEmpty()) continue;
            
            Ticker ticker = tickerData.get(0).getTicker();
            String symbol = ticker.getNormalizedSymbol();
            
            // Group by DEX
            Map<Long, List<FundingRateDaily>> dataByDex = tickerData.stream()
                    .collect(Collectors.groupingBy(d -> d.getDex().getId()));
            
            // ========== COMMON DATE RANGE CALCULATION ==========
            // Find the common start date: the most recent "minimum date" across all DEXes
            // This ensures we only compare data where ALL DEXes have entries
            LocalDate commonStartDate = dataByDex.values().stream()
                    .map(list -> list.stream()
                            .map(FundingRateDaily::getDate)
                            .min(LocalDate::compareTo)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)  // Take the most recent among the minimums
                    .orElse(actualStartDate);
            
            final LocalDate finalCommonStartDate = commonStartDate;
            
            List<DexRateDTO> dexRates = new ArrayList<>();
            
            for (Map.Entry<Long, List<FundingRateDaily>> dexEntry : dataByDex.entrySet()) {
                List<FundingRateDaily> dexData = dexEntry.getValue();
                String dexName = dexData.get(0).getDex().getName();
                
                // Filter to only include data from common start date onwards
                List<FundingRateDaily> filteredDexData = dexData.stream()
                        .filter(d -> !d.getDate().isBefore(finalCommonStartDate))
                        .toList();
                
                if (filteredDexData.isEmpty()) continue;
                
                // Calculate average of avgRateAnnual
                BigDecimal avgRate = filteredDexData.stream()
                        .map(FundingRateDaily::getAvgRateAnnual)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(filteredDexData.size()), 4, RoundingMode.HALF_UP);
                
                dexRates.add(DexRateDTO.builder()
                        .dex(dexName)
                        .rateAnnual(avgRate)
                        .build());
            }
            
            symbols.add(SymbolRatesDTO.builder()
                    .symbol(symbol)
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

