package com.crypto.funding.business;

import com.crypto.funding.dto.FundingRateDTO;
import com.crypto.funding.dto.MarketSymbolDTO;
import com.crypto.funding.exception.ResourceNotFoundException;
import com.crypto.funding.mapper.FundingRateMapper;
import com.crypto.funding.model.Dex;
import com.crypto.funding.model.FundingRate;
import com.crypto.funding.model.LastFunding;
import com.crypto.funding.model.Ticker;
import com.crypto.funding.repository.DexRepository;
import com.crypto.funding.repository.FundingRateRepository;
import com.crypto.funding.repository.LastFundingRepository;
import com.crypto.funding.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business logic for Funding Rate operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRateBusiness {

    private final FundingRateRepository fundingRateRepository;
    private final LastFundingRepository lastFundingRepository;
    private final DexRepository dexRepository;
    private final TickerRepository tickerRepository;
    private final FundingRateMapper fundingRateMapper;

    /**
     * Get all latest funding rates (one per dex/ticker)
     */
    @Cacheable(value = "latestFundingRates")
    public List<FundingRateDTO> getAllLatestRates() {
        List<LastFunding> rates = lastFundingRepository.findAll();
        return fundingRateMapper.lastFundingToDTOList(rates);
    }

    /**
     * Get funding rates by DEX
     */
    public List<FundingRateDTO> getRatesByDex(String dexName) {
        Dex dex = dexRepository.findByName(dexName)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "name", dexName));
        
        List<FundingRate> rates = fundingRateRepository.findByDexOrderByTimestampDesc(dex);
        return fundingRateMapper.toDTOList(rates);
    }

    /**
     * Get the latest funding rate for a symbol on a DEX
     */
    public Optional<FundingRateDTO> getLatestRate(String dexName, String symbol) {
        Optional<Dex> dexOpt = dexRepository.findByName(dexName);
        Optional<Ticker> tickerOpt = tickerRepository.findByNormalizedSymbol(symbol);
        
        if (dexOpt.isEmpty() || tickerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        return fundingRateRepository
                .findFirstByDexAndTickerOrderByTimestampDesc(dexOpt.get(), tickerOpt.get())
                .map(fundingRateMapper::toDTO);
    }

    /**
     * Get funding rate history
     */
    public List<FundingRateDTO> getHistory(String dexName, String symbol, int hours) {
        Dex dex = dexRepository.findByName(dexName)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "name", dexName));
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Ticker", "symbol", symbol));
        
        LocalDateTime startDate = LocalDateTime.now().minusHours(hours);
        List<FundingRate> rates = fundingRateRepository.findHistoricalData(dex, ticker, startDate);
        
        return fundingRateMapper.toDTOList(rates);
    }

    /**
     * Get all symbols for a DEX
     */
    public List<String> getSymbolsByDex(String dexName) {
        Dex dex = dexRepository.findByName(dexName)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "name", dexName));
        
        return fundingRateRepository.findDistinctTickersByDex(dex).stream()
                .map(Ticker::getNormalizedSymbol)
                .collect(Collectors.toList());
    }

    /**
     * Get all monitored symbols
     */
    public List<String> getAllMonitoredSymbols() {
        return lastFundingRepository.findDistinctTickers().stream()
                .map(Ticker::getNormalizedSymbol)
                .collect(Collectors.toList());
    }

    /**
     * Get all monitored symbols with icons
     */
    public List<MarketSymbolDTO> getAllMonitoredSymbolsWithIcons() {
        return lastFundingRepository.findDistinctTickers().stream()
                .map(ticker -> MarketSymbolDTO.builder()
                        .symbol(ticker.getNormalizedSymbol())
                        .iconUrl(ticker.getIconUrl())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Save a funding rate
     */
    @Transactional
    public FundingRateDTO saveFundingRate(FundingRate rate) {
        FundingRate saved = fundingRateRepository.save(rate);
        return fundingRateMapper.toDTO(saved);
    }

    /**
     * Search funding rates with filters
     */
    public List<FundingRateDTO> searchRates(String dexName, String symbol, Integer hours) {
        // Start with all latest rates
        List<LastFunding> rates = lastFundingRepository.findAll();
        
        // Apply filters
        return rates.stream()
                .filter(rate -> dexName == null || rate.getDexName().equalsIgnoreCase(dexName))
                .filter(rate -> symbol == null || rate.getSymbol().equalsIgnoreCase(symbol))
                .map(fundingRateMapper::toDTO)
                .collect(Collectors.toList());
    }
}
