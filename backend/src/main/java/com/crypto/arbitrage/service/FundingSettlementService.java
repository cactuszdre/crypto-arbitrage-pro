package com.crypto.arbitrage.service;

import com.crypto.arbitrage.funding.FundingRateNormalizer;
import com.crypto.arbitrage.funding.FundingRateUnit;
import com.crypto.arbitrage.funding.MarketScheduleSnapshot;
import com.crypto.arbitrage.funding.NormalizedFundingRate;
import com.crypto.arbitrage.funding.SettledFundingProvider;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.LastFunding;
import com.crypto.arbitrage.model.MarketFundingSchedule;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.FundingSettlementRepository;
import com.crypto.arbitrage.repository.LastFundingRepository;
import com.crypto.arbitrage.repository.MarketFundingScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundingSettlementService {

    private static final BigDecimal SANITY_ALERT_BPS = BigDecimal.valueOf(20); // 20% relative ≈ handled separately
    private static final BigDecimal SANITY_ALERT_ABS_BPS = BigDecimal.valueOf(5); // 5 bps hourly

    private final FundingSettlementRepository settlementRepository;
    private final MarketFundingScheduleRepository scheduleRepository;
    private final LastFundingRepository lastFundingRepository;
    private final List<SettledFundingProvider> settledProviders;

    @Transactional
    public FundingSettlement upsert(FundingSettlement incoming) {
        Optional<FundingSettlement> existing = settlementRepository.findByDexAndTickerAndSettlementTime(
                incoming.getDex(), incoming.getTicker(), incoming.getSettlementTime());

        attachPredictedDelta(incoming);

        if (existing.isPresent()) {
            FundingSettlement e = existing.get();
            e.setRate(incoming.getRate());
            e.setRate8h(incoming.getRate8h());
            e.setRateAnnual(incoming.getRateAnnual());
            e.setIntervalHours(incoming.getIntervalHours());
            e.setFundingPremium(incoming.getFundingPremium());
            e.setFundingIndex(incoming.getFundingIndex());
            e.setSource(incoming.getSource());
            e.setPredictedRate(incoming.getPredictedRate());
            e.setPredictedDeltaBps(incoming.getPredictedDeltaBps());
            return settlementRepository.save(e);
        }
        return settlementRepository.save(incoming);
    }

    @Transactional
    public int upsertAll(List<FundingSettlement> settlements) {
        int n = 0;
        for (FundingSettlement s : settlements) {
            upsert(s);
            n++;
        }
        return n;
    }

    @Transactional
    public void upsertSchedule(Dex dex, Ticker ticker, MarketScheduleSnapshot snap) {
        if (dex == null || ticker == null || snap == null) {
            return;
        }
        Optional<MarketFundingSchedule> existing = scheduleRepository.findByDexAndTicker(dex, ticker);
        MarketFundingSchedule schedule = existing.orElseGet(() -> MarketFundingSchedule.builder()
                .dex(dex)
                .ticker(ticker)
                .build());
        schedule.setIntervalHours(Math.max(1, snap.getIntervalHours()));
        schedule.setNextFundingTime(snap.getNextFundingTime());
        schedule.setContinuous(snap.isContinuous());
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);

        lastFundingRepository.findByTickerAndDex(ticker, dex).ifPresent(lf -> {
            if (snap.getNextFundingTime() != null) {
                lf.setNextFundingTime(snap.getNextFundingTime());
                lf.setIntervalHours(Math.max(1, snap.getIntervalHours()));
                lf.setUpdatedAt(LocalDateTime.now());
                lastFundingRepository.save(lf);
            }
        });
    }

    /**
     * Capture a settlement from the last predicted sample near T when no history API exists.
     */
    @Transactional
    public Optional<FundingSettlement> captureFromPredicted(Dex dex, Ticker ticker, LocalDateTime settlementTime,
                                                            int intervalHours, String source) {
        if (settlementRepository.existsByDexIdAndTickerIdAndSettlementTime(
                dex.getId(), ticker.getId(), settlementTime)) {
            return settlementRepository.findByDexAndTickerAndSettlementTime(dex, ticker, settlementTime);
        }

        LocalDateTime windowStart = settlementTime.minusMinutes(2);
        LocalDateTime windowEnd = settlementTime.plusMinutes(1);

        Optional<LastFunding> lf = lastFundingRepository.findByTickerAndDex(ticker, dex);
        BigDecimal rate = null;
        BigDecimal rate8h = null;
        BigDecimal rateAnnual = null;
        BigDecimal premium = null;

        if (lf.isPresent() && !lf.get().getTimestamp().isBefore(windowStart)
                && !lf.get().getTimestamp().isAfter(windowEnd)) {
            rate = lf.get().getRate();
            rate8h = lf.get().getRate8h();
            rateAnnual = lf.get().getRateAnnual();
            premium = lf.get().getFundingPremium();
        }

        if (rate == null) {
            // Fall back: any last_funding value (best effort for venues without history)
            if (lf.isPresent()) {
                rate = lf.get().getRate();
                rate8h = lf.get().getRate8h();
                rateAnnual = lf.get().getRateAnnual();
                premium = lf.get().getFundingPremium();
            }
        }

        if (rate == null) {
            return Optional.empty();
        }

        FundingSettlement settlement = FundingSettlement.builder()
                .dex(dex)
                .ticker(ticker)
                .settlementTime(settlementTime)
                .intervalHours(intervalHours)
                .rate(rate)
                .rate8h(rate8h)
                .rateAnnual(rateAnnual)
                .fundingPremium(premium)
                .source(source)
                .build();
        return Optional.of(upsert(settlement));
    }

    public Optional<SettledFundingProvider> findProvider(String dexName) {
        return settledProviders.stream()
                .filter(p -> p.getDexName().equalsIgnoreCase(dexName))
                .findFirst();
    }

    public List<SettledFundingProvider> getProviders() {
        return settledProviders;
    }

    /**
     * Backfill recent settled history for one market via its provider.
     */
    @Transactional
    public int backfill(String dexName, String normalizedSymbol, Instant from, Instant to) {
        Optional<SettledFundingProvider> provider = findProvider(dexName);
        if (provider.isEmpty() || !provider.get().supportsSettledHistory()) {
            return 0;
        }
        List<FundingSettlement> rows = provider.get().fetchSettledHistory(normalizedSymbol, from, to);
        return upsertAll(rows);
    }

    public static LocalDateTime truncateToHourUtc(LocalDateTime t) {
        return t.withMinute(0).withSecond(0).withNano(0);
    }

    public static LocalDateTime nextHourUtc(LocalDateTime now) {
        LocalDateTime truncated = truncateToHourUtc(now);
        if (!now.isAfter(truncated)) {
            return truncated;
        }
        return truncated.plusHours(1);
    }

    public static FundingSettlement buildFromHourly(
            Dex dex, Ticker ticker, LocalDateTime settlementTime, int intervalHours,
            BigDecimal hourlyRate, String source, BigDecimal premium, BigDecimal fundingIndex) {
        NormalizedFundingRate n = FundingRateNormalizer.normalize(hourlyRate, FundingRateUnit.HOURLY, intervalHours);
        return FundingSettlement.builder()
                .dex(dex)
                .ticker(ticker)
                .settlementTime(settlementTime)
                .intervalHours(intervalHours)
                .rate(n.getHourly())
                .rate8h(n.getRate8hPercent())
                .rateAnnual(n.getRateAnnualPercent())
                .fundingPremium(premium)
                .fundingIndex(fundingIndex)
                .source(source)
                .build();
    }

    private void attachPredictedDelta(FundingSettlement settlement) {
        Optional<LastFunding> lf = lastFundingRepository.findByTickerAndDex(
                settlement.getTicker(), settlement.getDex());
        if (lf.isEmpty() || settlement.getRate() == null) {
            return;
        }
        BigDecimal predicted = lf.get().getRate();
        settlement.setPredictedRate(predicted);
        // delta in bps of hourly rate: (pred - settled) / 1e-4
        BigDecimal delta = predicted.subtract(settlement.getRate())
                .multiply(BigDecimal.valueOf(10_000))
                .setScale(6, RoundingMode.HALF_UP);
        settlement.setPredictedDeltaBps(delta);

        if (delta.abs().compareTo(SANITY_ALERT_ABS_BPS) > 0) {
            BigDecimal rel = BigDecimal.ZERO;
            if (settlement.getRate().abs().compareTo(BigDecimal.ZERO) > 0) {
                rel = predicted.subtract(settlement.getRate())
                        .divide(settlement.getRate().abs(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            if (rel.abs().compareTo(SANITY_ALERT_BPS) > 0 || delta.abs().compareTo(SANITY_ALERT_ABS_BPS) > 0) {
                log.warn("Funding sanity: {} {} settled={} predicted={} deltaBps={} relPct={}",
                        settlement.getDexName(), settlement.getSymbol(),
                        settlement.getRate(), predicted, delta, rel);
            }
        }
    }
}
