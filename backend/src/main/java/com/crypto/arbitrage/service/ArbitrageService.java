package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.dto.DexRateDTO;
import com.crypto.arbitrage.dto.SymbolRatesDTO;
import com.crypto.arbitrage.model.LastFunding;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.LastFundingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArbitrageService {

    private final LastFundingRepository lastFundingRepository;

    public List<String> getDiagnosticInfo() {
        List<String> info = new ArrayList<>();
        List<LastFunding> allRates = lastFundingRepository.findAll();
        info.add("Total Latest Rates Found: " + allRates.size());

        // Group by ticker ID for efficiency
        Map<Integer, List<LastFunding>> ratesByTicker = allRates.stream()
                .filter(r -> r.getTicker() != null)
                .collect(Collectors.groupingBy(r -> r.getTicker().getId()));

        info.add("Unique Symbols Found: " + ratesByTicker.keySet().size());

        for (Map.Entry<Integer, List<LastFunding>> entry : ratesByTicker.entrySet()) {
            List<LastFunding> rates = entry.getValue();
            String symbol = rates.get(0).getSymbol();
            info.add("Symbol: " + symbol + " | Sources: " + rates.size());
            for (LastFunding rate : rates) {
                info.add("  -> DEX: " + (rate.getDex() != null ? rate.getDex().getName() : "null")
                        + " | AnnualRate: " + rate.getRateAnnual());
            }
        }
        return info;
    }

    @Cacheable(value = "arbitrageOpportunities")
    public List<ArbitrageOpportunityDTO> getArbitrageOpportunities() {
        // 1. Get latest rates from the optimized last_funding table
        List<LastFunding> allRates = lastFundingRepository.findAll();

        // 2. Group by ticker ID for efficiency
        Map<Integer, List<LastFunding>> ratesByTicker = allRates.stream()
                .filter(r -> r.getTicker() != null)
                .collect(Collectors.groupingBy(r -> r.getTicker().getId()));

        List<ArbitrageOpportunityDTO> opportunities = new ArrayList<>();

        // 3. Analyze each symbol
        for (Map.Entry<Integer, List<LastFunding>> entry : ratesByTicker.entrySet()) {
            List<LastFunding> rates = entry.getValue();
            String symbol = rates.get(0).getSymbol();

            if (rates.size() < 2) {
                continue; // Need at least 2 exchanges to compare
            }

            // Find best Long (Lowest Funding Rate) and best Short (Highest Funding Rate)
            LastFunding bestLong = rates.get(0);
            LastFunding bestShort = rates.get(0);

            for (LastFunding rate : rates) {
                if (rate.getRateAnnual().compareTo(bestLong.getRateAnnual()) < 0) {
                    bestLong = rate;
                }
                if (rate.getRateAnnual().compareTo(bestShort.getRateAnnual()) > 0) {
                    bestShort = rate;
                }
            }

            // If same DEX, no arb
            if (bestLong.getDex().getId().equals(bestShort.getDex().getId())) {
                continue;
            }

            // Calculate Spread
            BigDecimal spreadAnnual = bestShort.getRateAnnual().subtract(bestLong.getRateAnnual());

            // Only consider strictly positive spreads
            if (spreadAnnual.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Daily return estimation (Spread Annual / 365)
            BigDecimal dailyReturn = spreadAnnual.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);

            ArbitrageOpportunityDTO opportunity = ArbitrageOpportunityDTO.builder()
                    .symbol(symbol)
                    .longDex(bestLong.getDex().getName())
                    .shortDex(bestShort.getDex().getName())
                    .longRate(bestLong.getRateAnnual())
                    .shortRate(bestShort.getRateAnnual())
                    .spread(spreadAnnual)
                    .projectedDailyReturn(dailyReturn)
                    .detectedAt(LocalDateTime.now())
                    .build();

            opportunities.add(opportunity);
        }

        // Sort by spread descending
        opportunities.sort((o1, o2) -> o2.getSpread().compareTo(o1.getSpread()));

        return opportunities;
    }

    /**
     * Get all rates grouped by symbol for frontend-side arbitrage calculation.
     * This allows the frontend to recalculate best routes based on user-selected
     * DEX filters.
     *
     * @return List of symbols with all their DEX rates
     */
    @Cacheable(value = "allRatesBySymbol")
    public List<SymbolRatesDTO> getAllRatesBySymbol() {

        List<LastFunding> allRates = lastFundingRepository.findAll();

        // Group by ticker ID
        Map<Integer, List<LastFunding>> ratesByTicker = allRates.stream()
                .filter(r -> r.getTicker() != null && r.getDex() != null)
                .collect(Collectors.groupingBy(r -> r.getTicker().getId()));

        List<SymbolRatesDTO> result = new ArrayList<>();

        for (Map.Entry<Integer, List<LastFunding>> entry : ratesByTicker.entrySet()) {
            List<LastFunding> rates = entry.getValue();

            if (rates.isEmpty())
                continue;

            String symbol = rates.get(0).getSymbol();

            // Convert to DTOs
            List<DexRateDTO> dexRates = rates.stream()
                    .map(r -> DexRateDTO.builder()
                            .dex(r.getDex().getName())
                            .rateAnnual(r.getRateAnnual())
                            .build())
                    .collect(Collectors.toList());

            result.add(SymbolRatesDTO.builder()
                    .symbol(symbol)
                    .rates(dexRates)
                    .build());
        }

        // Sort by symbol name for consistent ordering
        result.sort((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol()));

        return result;
    }
}
