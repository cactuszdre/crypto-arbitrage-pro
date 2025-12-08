package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageDTO;
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
    public List<HistoricalArbitrageDTO> getHistoricalOpportunities(
            @RequestParam(defaultValue = "7") int days) {
        return historicalArbitrageService.getHistoricalOpportunities(days);
    }

    @GetMapping("/diagnose")
    public List<String> diagnose() {
        return arbitrageService.getDiagnosticInfo();
    }
}

