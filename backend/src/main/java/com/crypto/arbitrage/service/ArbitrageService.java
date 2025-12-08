package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.FundingRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final FundingRateRepository fundingRateRepository;

    public List<String> getDiagnosticInfo() {
        List<String> info = new ArrayList<>();
        List<FundingRate> allRates = fundingRateRepository.findAllLatestRates();
        info.add("Total Latest Rates Found: " + allRates.size());
        
        // Group by ticker ID for efficiency
        Map<Integer, List<FundingRate>> ratesByTicker = allRates.stream()
                .filter(r -> r.getTicker() != null)
                .collect(Collectors.groupingBy(r -> r.getTicker().getId()));
        
        info.add("Unique Symbols Found: " + ratesByTicker.keySet().size());
        
        for (Map.Entry<Integer, List<FundingRate>> entry : ratesByTicker.entrySet()) {
            List<FundingRate> rates = entry.getValue();
            String symbol = rates.get(0).getSymbol();
            info.add("Symbol: " + symbol + " | Sources: " + rates.size());
            for(FundingRate rate : rates) {
                info.add("  -> DEX: " + (rate.getDex() != null ? rate.getDex().getName() : "null") 
                        + " | AnnualRate: " + rate.getRateAnnual());
            }
        }
        return info;
    }

    public List<ArbitrageOpportunityDTO> getArbitrageOpportunities() {
        // 1. Get latest rates for all symbols/dexes
        List<FundingRate> allRates = fundingRateRepository.findAllLatestRates();

        // 2. Group by ticker ID for efficiency
        Map<Integer, List<FundingRate>> ratesByTicker = allRates.stream()
                .filter(r -> r.getTicker() != null)
                .collect(Collectors.groupingBy(r -> r.getTicker().getId()));

        List<ArbitrageOpportunityDTO> opportunities = new ArrayList<>();

        // 3. Analyze each symbol
        for (Map.Entry<Integer, List<FundingRate>> entry : ratesByTicker.entrySet()) {
            List<FundingRate> rates = entry.getValue();
            String symbol = rates.get(0).getSymbol();

            log.info("Analyzing symbol: {} with {} rates", symbol, rates.size());

            if (rates.size() < 2) {
                log.info("Skipping {} - insufficient data sources (count={})", symbol, rates.size());
                continue; // Need at least 2 exchanges to compare
            }

            // Find best Long (Lowest Funding Rate) and best Short (Highest Funding Rate)
            FundingRate bestLong = rates.get(0);
            FundingRate bestShort = rates.get(0);

            for (FundingRate rate : rates) {
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
}

