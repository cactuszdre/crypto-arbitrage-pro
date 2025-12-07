package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.FundingRateDTO;
import com.crypto.arbitrage.service.FundingRateService;
import com.crypto.arbitrage.service.ParadexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funding-rates")
@RequiredArgsConstructor
public class FundingRateController {

    private final FundingRateService fundingRateService;
    private final ParadexService paradexService;

    @GetMapping
    public ResponseEntity<List<FundingRateDTO>> getAllRates() {
        return ResponseEntity.ok(fundingRateService.getAllLatestRates());
    }

    @GetMapping("/dex/{dexName}")
    public ResponseEntity<List<FundingRateDTO>> getRatesByDex(@PathVariable String dexName) {
        return ResponseEntity.ok(fundingRateService.getRatesByDex(dexName));
    }

    @GetMapping("/dex/{dexName}/symbol/{symbol}")
    public ResponseEntity<FundingRateDTO> getLatestRate(
            @PathVariable String dexName,
            @PathVariable String symbol) {
        return fundingRateService.getLatestRate(dexName, symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<FundingRateDTO>> getHistory(
            @RequestParam String dex,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(fundingRateService.getHistory(dex, symbol, hours));
    }

    @GetMapping("/symbols/{dexName}")
    public ResponseEntity<List<String>> getSymbols(@PathVariable String dexName) {
        return ResponseEntity.ok(fundingRateService.getSymbolsByDex(dexName));
    }

    @GetMapping("/symbols")
    public ResponseEntity<List<com.crypto.arbitrage.dto.MarketSymbolDTO>> getAllSymbols() {
        return ResponseEntity.ok(fundingRateService.getAllMonitoredSymbolsWithIcons());
    }

    @GetMapping("/paradex/symbols")
    public ResponseEntity<List<String>> getParadexSymbols() {
        return ResponseEntity.ok(paradexService.fetchAvailableSymbols());
    }

    @PostMapping("/fetch/{symbol}")
    public ResponseEntity<FundingRateDTO> fetchAndSaveRate(@PathVariable String symbol) {
        return paradexService.fetchAndSaveLatestFundingRate(symbol)
                .map(rate -> FundingRateDTO.builder()
                        .dexName(rate.getDex().getName())
                        .symbol(rate.getSymbol())
                        .rate(rate.getRate())
                        .rate8h(rate.getRate8h())
                        .rateAnnual(rate.getRateAnnual())
                        .timestamp(rate.getTimestamp())
                        .build())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
