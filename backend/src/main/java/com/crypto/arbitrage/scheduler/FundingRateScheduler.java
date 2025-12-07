package com.crypto.arbitrage.scheduler;

import com.crypto.arbitrage.service.HyperliquidService;
import com.crypto.arbitrage.service.LighterService;
import com.crypto.arbitrage.service.ParadexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundingRateScheduler {

    private final ParadexService paradexService;
    private final LighterService lighterService;
    private final HyperliquidService hyperliquidService;
    private final com.crypto.arbitrage.repository.DexRepository dexRepository;
    private final com.crypto.arbitrage.repository.DexMarketRepository dexMarketRepository;


    /**
     * Récupère les funding rates toutes les 10 secondes
     */
    @Scheduled(fixedRate = 10000) // 10 secondes
    public void fetchFundingRatesLoop() {
        log.debug("=== Start periodic funding rate fetch ===");
        
        // 1. Fetch Paradex (Iterator based on active markets)
        try {
            dexRepository.findByName("Paradex").ifPresent(dex -> {
                List<com.crypto.arbitrage.model.DexMarket> markets = dexMarketRepository.findByDexAndActiveTrue(dex);
                if (markets.isEmpty()) {
                    log.debug("No active markets found for Paradex, skipping.");
                    return;
                }
                
                for (com.crypto.arbitrage.model.DexMarket market : markets) {
                    try {
                        paradexService.fetchAndSaveLatestFundingRate(market.getSymbol());
                    } catch (Exception e) {
                        log.error("Paradex: Error fetching {}", market.getSymbol(), e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error in Paradex fetch loop", e);
        }
        
        // 2. Fetch Lighter (Global fetch)
        try {
            lighterService.fetchFundingData();
        } catch (Exception e) {
            log.error("Lighter: Error fetching data", e);
        }

        // 3. Fetch Hyperliquid (Global fetch)
        try {
            hyperliquidService.fetchFundingData();
        } catch (Exception e) {
            log.error("Hyperliquid: Error fetching data", e);
        }
    }

    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        // Initial fetch logic if needed, but the loop handles it.
        // MarketSyncService will run at 2s to populate the list.
    }
}
