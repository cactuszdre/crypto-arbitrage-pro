package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.model.ArbitrageOpportunity;
import com.crypto.arbitrage.service.ArbitrageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/arbitrage")
@RequiredArgsConstructor
public class ArbitrageController {

    private final ArbitrageService arbitrageService;

    @GetMapping("/opportunities")
    public List<ArbitrageOpportunityDTO> getOpportunities() {
        return arbitrageService.getArbitrageOpportunities();
    }

    @GetMapping("/diagnose")
    public List<String> diagnose() {
        return arbitrageService.getDiagnosticInfo();
    }
}
