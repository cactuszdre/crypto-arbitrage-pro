package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.FundingRateDTO;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate;
import com.crypto.arbitrage.model.LastFunding;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRateRepository;
import com.crypto.arbitrage.repository.LastFundingRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRateService {

    private final FundingRateRepository fundingRateRepository;
    private final LastFundingRepository lastFundingRepository;
    private final DexRepository dexRepository;
    private final TickerRepository tickerRepository;
    private final com.crypto.arbitrage.repository.DexMarketRepository dexMarketRepository;
    private final com.crypto.arbitrage.repository.FundingSettlementRepository fundingSettlementRepository;

    /**
     * Récupère tous les derniers funding rates (un seul par dex/ticker)
     * Uses the optimized last_funding table instead of expensive subquery
     * Cached for 10 seconds to reduce DB load
     */
    @Cacheable(value = "latestFundingRates")
    public List<FundingRateDTO> getAllLatestRates() {
        // Use the optimized last_funding table
        List<LastFunding> rates = lastFundingRepository.findAll();
        return rates.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les funding rates par DEX
     */
    public List<FundingRateDTO> getRatesByDex(String dexName) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        if (dexOpt.isEmpty()) {
            return List.of();
        }
        
        List<FundingRate> rates = fundingRateRepository.findByDexOrderByTimestampDesc(dexOpt.get());
        return rates.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupère le dernier funding rate pour un symbole sur un DEX
     */
    public Optional<FundingRateDTO> getLatestRate(String dexName, String symbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        Optional<Ticker> tickerOpt = tickerRepository.findByNormalizedSymbol(symbol);
        
        if (dexOpt.isEmpty() || tickerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        return fundingRateRepository
                .findFirstByDexAndTickerOrderByTimestampDesc(dexOpt.get(), tickerOpt.get())
                .map(this::toDTO);
    }

    /**
     * Historical rates: settled prints where available, older predicted samples to fill
     * the requested window before the earliest settlement.
     */
    public List<FundingRateDTO> getHistory(String dexName, String symbol, int hours) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        Optional<Ticker> tickerOpt = tickerRepository.findByNormalizedSymbol(symbol);
        
        if (dexOpt.isEmpty() || tickerOpt.isEmpty()) {
            return List.of();
        }

        LocalDateTime end = LocalDateTime.ofInstant(java.time.Instant.now(), java.time.ZoneOffset.UTC);
        LocalDateTime startDate = end.minusHours(hours);
        List<com.crypto.arbitrage.model.FundingSettlement> settlements =
                fundingSettlementRepository.findByTickerAndDexBetween(
                        tickerOpt.get(), dexOpt.get(), startDate, end);

        List<FundingRate> predictedRates = fundingRateRepository.findHistoricalData(
                dexOpt.get(), tickerOpt.get(), startDate);

        if (settlements.isEmpty()) {
            return predictedRates.stream().map(this::toDTO).collect(Collectors.toList());
        }

        LocalDateTime earliestSettled = settlements.stream()
                .map(com.crypto.arbitrage.model.FundingSettlement::getSettlementTime)
                .min(LocalDateTime::compareTo)
                .orElse(startDate);

        List<FundingRateDTO> olderPredicted = predictedRates.stream()
                .filter(r -> r.getTimestamp() != null && r.getTimestamp().isBefore(earliestSettled))
                .map(this::toDTO)
                .collect(Collectors.toList());

        List<FundingRateDTO> settledDtos = settlements.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        List<FundingRateDTO> merged = new ArrayList<>(olderPredicted.size() + settledDtos.size());
        merged.addAll(olderPredicted);
        merged.addAll(settledDtos);
        return merged;
    }

    /**
     * Récupère tous les symboles disponibles pour un DEX
     */
    public List<String> getSymbolsByDex(String dexName) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        if (dexOpt.isEmpty()) {
            return List.of();
        }
        
        return fundingRateRepository.findDistinctTickersByDex(dexOpt.get()).stream()
                .map(Ticker::getNormalizedSymbol)
                .collect(Collectors.toList());
    }

    /**
     * Récupère la liste globale de tous les symboles surveillés
     */
    public List<String> getAllMonitoredSymbols() {
        return dexMarketRepository.findDistinctActiveSymbols();
    }

    public List<com.crypto.arbitrage.dto.MarketSymbolDTO> getAllMonitoredSymbolsWithIcons() {
        return dexMarketRepository.findDistinctSymbolsWithIcons();
    }

    /**
     * Sauvegarde un funding rate
     */
    @Transactional
    public FundingRateDTO saveFundingRate(FundingRate rate) {
        FundingRate saved = fundingRateRepository.save(rate);
        return toDTO(saved);
    }

    private FundingRateDTO toDTO(FundingRate rate) {
        String direction = rate.getRate().compareTo(BigDecimal.ZERO) > 0 
                ? "LONGS_PAY" 
                : "SHORTS_PAY";
        
        return FundingRateDTO.builder()
                .id(rate.getId())
                .dexName(rate.getDex().getName())
                .symbol(rate.getSymbol())
                .rate(rate.getRate())
                .rate8h(rate.getRate8h())
                .rateAnnual(rate.getRateAnnual())
                .fundingPremium(rate.getFundingPremium())
                .intervalHours(rate.getIntervalHours())
                .timestamp(rate.getTimestamp())
                .nextFundingTime(rate.getNextFundingTime())
                .rateKind("PREDICTED")
                .direction(direction)
                .build();
    }

    private FundingRateDTO toDTO(LastFunding rate) {
        String direction = rate.getRate().compareTo(BigDecimal.ZERO) > 0 
                ? "LONGS_PAY" 
                : "SHORTS_PAY";
        
        return FundingRateDTO.builder()
                .id(rate.getId())
                .dexName(rate.getDexName())
                .symbol(rate.getSymbol())
                .rate(rate.getRate())
                .rate8h(rate.getRate8h())
                .rateAnnual(rate.getRateAnnual())
                .fundingPremium(rate.getFundingPremium())
                .intervalHours(rate.getIntervalHours())
                .timestamp(rate.getTimestamp())
                .nextFundingTime(rate.getNextFundingTime())
                .rateKind("PREDICTED")
                .direction(direction)
                .build();
    }

    private FundingRateDTO toDTO(com.crypto.arbitrage.model.FundingSettlement s) {
        String direction = s.getRate().compareTo(BigDecimal.ZERO) > 0
                ? "LONGS_PAY"
                : "SHORTS_PAY";
        return FundingRateDTO.builder()
                .id(s.getId())
                .dexName(s.getDexName())
                .symbol(s.getSymbol())
                .rate(s.getRate())
                .rate8h(s.getRate8h())
                .rateAnnual(s.getRateAnnual())
                .fundingPremium(s.getFundingPremium())
                .intervalHours(s.getIntervalHours())
                .timestamp(s.getSettlementTime())
                .rateKind("SETTLED")
                .direction(direction)
                .build();
    }
}

