package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.funding.SettledFundingProvider;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.model.DexMarket;
import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.repository.DexMarketRepository;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.FundingSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One-shot backfill of settled funding history after startup (HL + Ondo first, then others).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettledFundingBackfillRunner {

    private static final Set<String> PRIORITY = Set.of("Hyperliquid", "Ondo Perps");

    private final List<SettledFundingProvider> providers;
    private final DexRepository dexRepository;
    private final DexMarketRepository dexMarketRepository;
    private final FundingSettlementService settlementService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(this::runBackfill, "settled-funding-backfill");
        t.setDaemon(true);
        t.start();
    }

    private void runBackfill() {
        try {
            Thread.sleep(8_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Instant to = Instant.now();
        Instant from = to.minusSeconds(48L * 3600L);
        int total = 0;

        // Priority venues first (V1)
        total += backfillGroup(PRIORITY, from, to);
        // Remaining venues
        for (SettledFundingProvider provider : providers) {
            if (PRIORITY.contains(provider.getDexName()) || !provider.supportsSettledHistory()) {
                continue;
            }
            total += backfillDex(provider, from, to);
        }

        log.info("Startup settled funding backfill complete: {} rows upserted", total);
    }

    private int backfillGroup(Set<String> names, Instant from, Instant to) {
        int n = 0;
        for (SettledFundingProvider provider : providers) {
            if (names.contains(provider.getDexName()) && provider.supportsSettledHistory()) {
                n += backfillDex(provider, from, to);
            }
        }
        return n;
    }

    private int backfillDex(SettledFundingProvider provider, Instant from, Instant to) {
        Optional<Dex> dexOpt = dexRepository.findByName(provider.getDexName());
        if (dexOpt.isEmpty() || Boolean.FALSE.equals(dexOpt.get().getActive())) {
            return 0;
        }
        Dex dex = dexOpt.get();
        int n = 0;
        List<DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
        // Cap initial backfill to avoid hammering APIs
        int limit = Math.min(markets.size(), 40);
        for (int i = 0; i < limit; i++) {
            DexMarket market = markets.get(i);
            try {
                List<FundingSettlement> rows = provider.fetchSettledHistory(market.getSymbol(), from, to);
                n += settlementService.upsertAll(rows);
            } catch (Exception e) {
                log.debug("Backfill {} {}: {}", provider.getDexName(), market.getSymbol(), e.getMessage());
            }
        }
        if (n > 0) {
            log.info("Backfilled {} settled rows for {}", n, provider.getDexName());
        }
        return n;
    }
}
