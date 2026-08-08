package com.crypto.arbitrage.service;

import com.crypto.arbitrage.dto.FundingRateChartDTO;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingRate5Min;
import com.crypto.arbitrage.model.FundingRateDaily;
import com.crypto.arbitrage.model.FundingRateHourly;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingRate5MinRepository;
import com.crypto.arbitrage.repository.FundingRateDailyRepository;
import com.crypto.arbitrage.repository.FundingRateHourlyRepository;
import com.crypto.arbitrage.repository.FundingSettlementRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chart data: merge settled prints per DEX with predicted aggregates as fallback.
 * Query windows use UTC to match settlement timestamps.
 */
@Service
@RequiredArgsConstructor
@Slf4j
// FundingRate5Min/Hourly/Daily map dex lazily; with open-in-view disabled the
// session must be owned here rather than by the request.
@Transactional(readOnly = true)
public class FundingRateChartService {

    private final FundingRate5MinRepository fundingRate5MinRepository;
    private final FundingRateHourlyRepository fundingRateHourlyRepository;
    private final FundingRateDailyRepository fundingRateDailyRepository;
    private final FundingSettlementRepository fundingSettlementRepository;
    private final TickerRepository tickerRepository;
    private final DexRepository dexRepository;

    public List<FundingRateChartDTO> getChartData(String symbol, String dexName, int hours) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        Dex dex = dexRepository.findByName(dexName).orElse(null);
        if (ticker == null || dex == null) {
            log.warn("Ticker or DEX not found: symbol={}, dex={}", symbol, dexName);
            return Collections.emptyList();
        }

        LocalDateTime endTime = utcNow();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<FundingSettlement> settlements =
                fundingSettlementRepository.findByTickerAndDexBetween(ticker, dex, startTime, endTime);
        List<FundingRateChartDTO> predicted = fundingRate5MinRepository
                .findChartData(ticker, dex, startTime, endTime).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return mergeSettledWithPredicted(settlements, predicted, startTime);
    }

    public List<FundingRateChartDTO> getChartDataBySymbol(String symbol, int hours) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        if (ticker == null) {
            log.warn("Ticker not found: {}", symbol);
            return Collections.emptyList();
        }

        LocalDateTime endTime = utcNow();
        LocalDateTime startTime = endTime.minusHours(hours);

        Map<String, List<FundingSettlement>> settledByDex =
                fundingSettlementRepository.findByTickerBetween(ticker, startTime, endTime).stream()
                        .collect(Collectors.groupingBy(FundingSettlement::getDexName));

        Map<String, List<FundingRateChartDTO>> predictedByDex =
                fundingRate5MinRepository.findChartDataByTicker(ticker, startTime, endTime).stream()
                        .map(this::toDTO)
                        .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));

        return mergeByDex(settledByDex, predictedByDex, startTime);
    }

    public List<FundingRateChartDTO> getChartDataByDex(String dexName, int hours) {
        Dex dex = dexRepository.findByName(dexName).orElse(null);
        if (dex == null) {
            log.warn("DEX not found: {}", dexName);
            return Collections.emptyList();
        }

        LocalDateTime endTime = utcNow();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<FundingSettlement> settlements =
                fundingSettlementRepository.findByDexBetween(dex, startTime, endTime);
        List<FundingRateChartDTO> predicted = fundingRate5MinRepository
                .findChartDataByDex(dex, startTime, endTime).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return mergeSettledWithPredicted(settlements, predicted, startTime);
    }

    public List<FundingRateChartDTO> getLatestDataPoints(String symbol, String dexName, int count) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        Dex dex = dexRepository.findByName(dexName).orElse(null);
        if (ticker == null || dex == null) {
            return Collections.emptyList();
        }

        LocalDateTime endTime = utcNow();
        LocalDateTime startTime = endTime.minusHours(Math.max(count, 24));
        List<FundingSettlement> settlements =
                fundingSettlementRepository.findByTickerAndDexBetween(ticker, dex, startTime, endTime);
        if (!settlements.isEmpty()) {
            int from = Math.max(0, settlements.size() - count);
            return settlements.subList(from, settlements.size()).stream()
                    .map(this::toSettlementDTO)
                    .collect(Collectors.toList());
        }

        List<FundingRate5Min> data = fundingRate5MinRepository.findLatestBuckets(
                ticker, dex, PageRequest.of(0, count));
        Collections.reverse(data);
        return data.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Map<String, List<FundingRateChartDTO>> getComparisonData(String symbol, int hours) {
        return getChartDataBySymbol(symbol, hours).stream()
                .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));
    }

    public Map<String, List<FundingRateChartDTO>> getDailyComparisonData(String symbol, int days) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        if (ticker == null) {
            log.warn("Ticker not found: {}", symbol);
            return Collections.emptyMap();
        }

        LocalDateTime start = utcNow().toLocalDate().minusDays(days).atStartOfDay();
        LocalDateTime end = utcNow();

        Map<String, List<FundingSettlement>> settledByDex =
                fundingSettlementRepository.findByTickerBetween(ticker, start, end).stream()
                        .collect(Collectors.groupingBy(FundingSettlement::getDexName));

        LocalDate startDate = utcNow().toLocalDate().minusDays(days);
        Map<String, List<FundingRateChartDTO>> predictedByDex =
                fundingRateDailyRepository.findByTickerSince(ticker, startDate).stream()
                        .map(this::toDailyDTO)
                        .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));

        List<FundingRateChartDTO> merged = mergeByDex(settledByDex, predictedByDex, start);
        return merged.stream().collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));
    }

    public Map<String, List<FundingRateChartDTO>> getHourlyComparisonData(String symbol, int hours) {
        Ticker ticker = tickerRepository.findByNormalizedSymbol(symbol).orElse(null);
        if (ticker == null) {
            log.warn("Ticker not found: {}", symbol);
            return Collections.emptyMap();
        }

        LocalDateTime endTime = utcNow();
        LocalDateTime startTime = endTime.minusHours(hours);

        Map<String, List<FundingSettlement>> settledByDex =
                fundingSettlementRepository.findByTickerBetween(ticker, startTime, endTime).stream()
                        .collect(Collectors.groupingBy(FundingSettlement::getDexName));

        Map<String, List<FundingRateChartDTO>> predictedByDex =
                fundingRateHourlyRepository.findChartDataByTicker(ticker, startTime, endTime).stream()
                        .map(this::toHourlyDTO)
                        .collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));

        List<FundingRateChartDTO> merged = mergeByDex(settledByDex, predictedByDex, startTime);
        return merged.stream().collect(Collectors.groupingBy(FundingRateChartDTO::getDexName));
    }

    /**
     * Per-DEX merge: use settled series when present for that DEX; otherwise predicted fallback.
     * When settled exists but starts after {@code startTime}, prepend older predicted points.
     */
    private List<FundingRateChartDTO> mergeByDex(
            Map<String, List<FundingSettlement>> settledByDex,
            Map<String, List<FundingRateChartDTO>> predictedByDex,
            LocalDateTime startTime) {
        Set<String> dexNames = new HashSet<>();
        dexNames.addAll(settledByDex.keySet());
        dexNames.addAll(predictedByDex.keySet());

        List<FundingRateChartDTO> out = new ArrayList<>();
        for (String dexName : dexNames) {
            List<FundingSettlement> settled = settledByDex.getOrDefault(dexName, List.of());
            List<FundingRateChartDTO> predicted = predictedByDex.getOrDefault(dexName, List.of());
            out.addAll(mergeSettledWithPredicted(settled, predicted, startTime));
        }
        out.sort((a, b) -> {
            int c = a.getBucketTime().compareTo(b.getBucketTime());
            if (c != 0) return c;
            String da = a.getDexName() != null ? a.getDexName() : "";
            String db = b.getDexName() != null ? b.getDexName() : "";
            return da.compareToIgnoreCase(db);
        });
        return out;
    }

    private List<FundingRateChartDTO> mergeSettledWithPredicted(
            List<FundingSettlement> settlements,
            List<FundingRateChartDTO> predicted,
            LocalDateTime startTime) {
        if (settlements == null || settlements.isEmpty()) {
            return predicted != null ? predicted : List.of();
        }

        List<FundingRateChartDTO> settledDtos = settlements.stream()
                .map(this::toSettlementDTO)
                .collect(Collectors.toList());

        LocalDateTime earliestSettled = settlements.stream()
                .map(FundingSettlement::getSettlementTime)
                .min(LocalDateTime::compareTo)
                .orElse(startTime);

        List<FundingRateChartDTO> olderPredicted = predicted == null ? List.of() : predicted.stream()
                .filter(p -> p.getBucketTime() != null && p.getBucketTime().isBefore(earliestSettled))
                .collect(Collectors.toList());

        List<FundingRateChartDTO> merged = new ArrayList<>(olderPredicted.size() + settledDtos.size());
        merged.addAll(olderPredicted);
        merged.addAll(settledDtos);
        return merged;
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

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

    private FundingRateChartDTO toHourlyDTO(FundingRateHourly entity) {
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

    private FundingRateChartDTO toSettlementDTO(FundingSettlement s) {
        return FundingRateChartDTO.builder()
                .id(s.getId())
                .symbol(s.getSymbol())
                .dexName(s.getDexName())
                .bucketTime(s.getSettlementTime())
                .avgRate(s.getRate())
                .avgRate8h(s.getRate8h())
                .avgRateAnnual(s.getRateAnnual())
                .openRate(s.getRate())
                .closeRate(s.getRate())
                .minRate(s.getRate())
                .maxRate(s.getRate())
                .sampleCount(1)
                .build();
    }

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
            return ChartStatsSummary.builder().dataPointCount(0).build();
        }
    }
}
