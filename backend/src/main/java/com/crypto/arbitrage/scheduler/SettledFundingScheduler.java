package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.funding.FundingModel;
import com.crypto.arbitrage.funding.SettledFundingProvider;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.MarketFundingSchedule;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.repository.FundingSettlementRepository;
import com.crypto.arbitrage.repository.MarketFundingScheduleRepository;
import com.crypto.arbitrage.service.FundingSettlementService;
import com.crypto.arbitrage.service.TickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Captures settled (paid) funding prints after each interval boundary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettledFundingScheduler {

    private final List<SettledFundingProvider> providers;
    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;
    private final MarketFundingScheduleRepository scheduleRepository;
    private final FundingSettlementRepository settlementRepository;
    private final FundingSettlementService settlementService;
    private final TickerService tickerService;

    /**
     * Every minute: for settlements that occurred 45s–5min ago, fetch history (or snapshot).
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void captureRecentSettlements() {
        LocalDateTime nowUtc = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        LocalDateTime windowStart = nowUtc.minusMinutes(5);
        LocalDateTime windowEnd = nowUtc.minusSeconds(45);

        int saved = 0;
        for (SettledFundingProvider provider : providers) {
            if (provider.getFundingModel() == FundingModel.CONTINUOUS_INDEX) {
                continue;
            }

            Optional<Dex> dexOpt = dexRepository.findByName(provider.getDexName());
            if (dexOpt.isEmpty() || Boolean.FALSE.equals(dexOpt.get().getActive())) {
                continue;
            }
            Dex dex = dexOpt.get();
            List<DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
            if (markets.isEmpty()) {
                continue;
            }

            LocalDateTime cursor = FundingSettlementService.truncateToHourUtc(windowStart);
            LocalDateTime lastHour = FundingSettlementService.truncateToHourUtc(windowEnd);

            while (!cursor.isAfter(lastHour)) {
                LocalDateTime settlementTime = cursor;
                cursor = cursor.plusHours(1);

                if (settlementTime.isBefore(windowStart.minusMinutes(1))
                        || settlementTime.isAfter(windowEnd.plusMinutes(1))) {
                    continue;
                }

                for (DexMarket market : markets) {
                    try {
                        saved += captureOne(provider, dex, market.getSymbol(), settlementTime);
                    } catch (Exception e) {
                        log.debug("Settlement capture failed {} {}: {}",
                                provider.getDexName(), market.getSymbol(), e.getMessage());
                    }
                }
            }
        }

        if (saved > 0) {
            log.info("Captured {} settled funding rows", saved);
        }
    }

    /**
     * Hourly backfill of last 6h settled history for history-capable venues.
     */
    @Scheduled(cron = "0 10 * * * *")
    public void backfillRecentHistory() {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(6 * 3600);
        int total = 0;

        for (SettledFundingProvider provider : providers) {
            if (!provider.supportsSettledHistory()) {
                continue;
            }
            Optional<Dex> dexOpt = dexRepository.findByName(provider.getDexName());
            if (dexOpt.isEmpty() || Boolean.FALSE.equals(dexOpt.get().getActive())) {
                continue;
            }
            Dex dex = dexOpt.get();
            for (DexMarket market : dexMarketRepository.findByDexAndActiveTrue(dex)) {
                try {
                    List<FundingSettlement> rows = provider.fetchSettledHistory(market.getSymbol(), from, to);
                    total += settlementService.upsertAll(rows);
                } catch (Exception e) {
                    log.warn("Backfill failed {} {}: {}", provider.getDexName(), market.getSymbol(), e.getMessage());
                }
            }
        }
        if (total > 0) {
            log.info("Settled funding backfill upserted {} rows", total);
        }
    }

    private int captureOne(SettledFundingProvider provider, Dex dex, String symbol,
                           LocalDateTime settlementTime) {
        Ticker ticker = tickerService.getOrCreate(symbol);

        int intervalHours = 1;
        Optional<MarketFundingSchedule> schedule = scheduleRepository.findByDexAndTicker(dex, ticker);
        if (schedule.isPresent()) {
            if (Boolean.TRUE.equals(schedule.get().getContinuous())) {
                return 0;
            }
            intervalHours = Math.max(1, schedule.get().getIntervalHours());
        }

        // Only capture on real interval boundaries (e.g. every 8h for Variational 8h markets)
        if (!isIntervalBoundary(settlementTime, intervalHours)) {
            return 0;
        }

        if (settlementRepository.existsByDexIdAndTickerIdAndSettlementTime(
                dex.getId(), ticker.getId(), settlementTime)) {
            return 0;
        }

        if (provider.supportsSettledHistory()) {
            Instant from = settlementTime.toInstant(ZoneOffset.UTC).minusSeconds(60);
            Instant to = settlementTime.toInstant(ZoneOffset.UTC).plusSeconds(3600L * intervalHours);
            List<FundingSettlement> rows = provider.fetchSettledHistory(symbol, from, to);
            int n = 0;
            for (FundingSettlement row : rows) {
                if (row.getSettlementTime().equals(settlementTime)
                        || row.getSettlementTime().equals(settlementTime.withSecond(0).withNano(0))) {
                    settlementService.upsert(row);
                    n++;
                } else if (!row.getSettlementTime().isBefore(settlementTime.minusMinutes(5))
                        && !row.getSettlementTime().isAfter(settlementTime.plusMinutes(5))) {
                    row.setSettlementTime(settlementTime);
                    settlementService.upsert(row);
                    n++;
                }
            }
            return n;
        }

        // Predicted snapshot fallback (Nado / Variational without history)
        return settlementService.captureFromPredicted(
                dex, ticker, settlementTime, intervalHours,
                provider.getDexName().toLowerCase().replace(' ', '_') + "_predicted_snapshot")
                .isPresent() ? 1 : 0;
    }

    /**
     * True when {@code settlementTime} aligns to an N-hour UTC boundary (unix epoch hours % N == 0).
     */
    static boolean isIntervalBoundary(LocalDateTime settlementTimeUtc, int intervalHours) {
        int n = Math.max(1, intervalHours);
        if (n == 1) {
            return true;
        }
        long epochHour = settlementTimeUtc.toEpochSecond(ZoneOffset.UTC) / 3600L;
        return epochHour % n == 0;
    }
}
