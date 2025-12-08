package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.HistoricalArbitrageDTO;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    /**
     * Get historical arbitrage opportunities for a given time period.
     *
     * @param days Number of days to look back (1, 7, 30, 90, 180, 365)
     * @return List of arbitrage opportunities averaged over the period
     */
    public List<HistoricalArbitrageDTO> getHistoricalOpportunities(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDate endDate = LocalDate.now().minusDays(1); // Up to yesterday (today's data is still being collected)
        
        log.info("Calculating historical arbitrage for {} days (from {} to {})", days, startDate, endDate);
        
        // Get all daily data for the period
        List<FundingRateDaily> allDailyData = fundingRateDailyRepository.findAllSince(startDate);
        
        if (allDailyData.isEmpty()) {
            log.info("No historical data found for period");
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
            
            // Group by DEX and calculate averages
            Map<Long, List<FundingRateDaily>> dataByDex = tickerData.stream()
                    .collect(Collectors.groupingBy(d -> d.getDex().getId()));
            
            // Need at least 2 DEXes for arbitrage
            if (dataByDex.size() < 2) continue;
            
            // Calculate average annual rate for each DEX
            Map<String, BigDecimal> dexAvgRates = new HashMap<>();
            Map<String, Integer> dexSampleCounts = new HashMap<>();
            
            for (Map.Entry<Long, List<FundingRateDaily>> dexEntry : dataByDex.entrySet()) {
                List<FundingRateDaily> dexData = dexEntry.getValue();
                String dexName = dexData.get(0).getDex().getName();
                
                // Calculate average of avgRateAnnual
                BigDecimal avgRate = dexData.stream()
                        .map(FundingRateDaily::getAvgRateAnnual)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(dexData.size()), 4, RoundingMode.HALF_UP);
                
                dexAvgRates.put(dexName, avgRate);
                dexSampleCounts.put(dexName, dexData.size());
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
            
            // Calculate spread
            BigDecimal spread = highestRate.subtract(lowestRate);
            
            // Only positive spreads
            if (spread.compareTo(BigDecimal.ZERO) <= 0) continue;
            
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
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
            
            opportunities.add(dto);
        }
        
        // Sort by spread descending
        opportunities.sort((a, b) -> b.getSpread().compareTo(a.getSpread()));
        
        log.info("Found {} historical arbitrage opportunities", opportunities.size());
        return opportunities;
    }
}
