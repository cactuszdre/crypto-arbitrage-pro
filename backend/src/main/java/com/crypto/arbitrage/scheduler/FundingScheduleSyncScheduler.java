package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.funding.MarketScheduleSnapshot;
import com.crypto.arbitrage.funding.SettledFundingProvider;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.FundingSettlementService;
import com.crypto.arbitrage.service.TickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Refreshes per-(dex,ticker) funding interval and nextFundingTime.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FundingScheduleSyncScheduler {

    private final List<SettledFundingProvider> providers;
    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;
    private final FundingSettlementService settlementService;
    private final TickerService tickerService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 15_000)
    public void syncSchedules() {
        int n = 0;
        for (SettledFundingProvider provider : providers) {
            Optional<Dex> dexOpt = dexRepository.findByName(provider.getDexName());
            if (dexOpt.isEmpty() || Boolean.FALSE.equals(dexOpt.get().getActive())) {
                continue;
            }
            Dex dex = dexOpt.get();

            int batch = provider.syncAllSchedules();
            if (batch > 0) {
                n += batch;
                continue;
            }

            List<DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
            for (DexMarket market : markets) {
                try {
                    Optional<MarketScheduleSnapshot> snap = provider.fetchSchedule(market.getSymbol());
                    if (snap.isEmpty()) {
                        continue;
                    }
                    Ticker ticker = tickerService.getOrCreate(market.getSymbol());
                    settlementService.upsertSchedule(dex, ticker, snap.get());
                    n++;
                } catch (Exception e) {
                    log.debug("Schedule sync failed {} {}: {}",
                            provider.getDexName(), market.getSymbol(), e.getMessage());
                }
            }
        }
        if (n > 0) {
            log.info("Synced {} market funding schedules", n);
        }
    }
}
