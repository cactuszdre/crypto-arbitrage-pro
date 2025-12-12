package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageResponse;
import com.crypto.arbitrage.dto.HistoricalRatesResponse;
import com.crypto.arbitrage.dto.SymbolRatesDTO;
import com.crypto.arbitrage.service.ArbitrageService;
import com.crypto.arbitrage.service.HistoricalArbitrageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/arbitrage")
@RequiredArgsConstructor
public class ArbitrageController {

    private final ArbitrageService arbitrageService;
    private final HistoricalArbitrageService historicalArbitrageService;

    @GetMapping("/opportunities")
    public List<ArbitrageOpportunityDTO> getOpportunities() {
        return arbitrageService.getArbitrageOpportunities();
    }

    @GetMapping("/opportunities/historical")
    public HistoricalArbitrageResponse getHistoricalOpportunities(
            @RequestParam(defaultValue = "7") int days) {
        return historicalArbitrageService.getHistoricalOpportunities(days);
    }

    /**
     * Get all rates grouped by symbol for frontend-side arbitrage calculation.
     * This allows dynamic recalculation based on user-selected DEX filters.
     */
    @GetMapping("/rates/all")
    public List<SymbolRatesDTO> getAllRates() {
        return arbitrageService.getAllRatesBySymbol();
    }

    /**
     * Get all historical rates grouped by symbol for frontend-side arbitrage calculation.
     * Returns average rates per DEX for each symbol over the requested period.
     */
    @GetMapping("/rates/historical")
    public HistoricalRatesResponse getHistoricalRates(
            @RequestParam(defaultValue = "7") int days) {
        return historicalArbitrageService.getAllHistoricalRatesBySymbol(days);
    }

    @GetMapping("/diagnose")
    public List<String> diagnose() {
        return arbitrageService.getDiagnosticInfo();
    }
}

