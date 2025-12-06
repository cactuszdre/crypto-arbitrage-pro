package com.crypto.arbitrage.scheduler;

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
    private final LighterService lighterService; // Inject LighterService

    // Symboles à surveiller par défaut pour Paradex
    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "BTC-USD-PERP",
            "ETH-USD-PERP",
            "SOL-USD-PERP",
            "RESOLV-USD-PERP"
    );

    /**
     * Récupère les funding rates toutes les 10 secondes
     */
    @Scheduled(fixedRate = 10000) // 10 secondes en millisecondes
    public void fetchFundingRatesHourly() {
        log.info("=== Démarrage de la récupération périodique des funding rates ===");
        
        // 1. Fetch Paradex
        for (String symbol : DEFAULT_SYMBOLS) {
            try {
                paradexService.fetchAndSaveLatestFundingRate(symbol);
                log.info("Paradex: Funding rate récupéré pour {}", symbol);
            } catch (Exception e) {
                log.error("Paradex: Erreur lors de la récupération pour {}: {}", 
                        symbol, e.getMessage());
            }
        }
        
        // 2. Fetch Lighter (fetches all rates at once)
        try {
            lighterService.fetchFundingData();
            log.info("Lighter: Funding rates global fetch completed");
        } catch (Exception e) {
            log.error("Lighter: Erreur lors de la récupération globale: {}", e.getMessage());
        }
        
        log.info("=== Fin de la récupération périodique des funding rates ===");
    }

    /**
     * Récupère les funding rates au démarrage de l'application
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void fetchFundingRatesOnStartup() {
        log.info("=== Récupération initiale des funding rates au démarrage ===");
        fetchFundingRatesHourly();
    }
}
