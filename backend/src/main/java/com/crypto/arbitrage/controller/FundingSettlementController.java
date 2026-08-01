package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.model.FundingSettlement;
import com.crypto.arbitrage.model.Ticker;
import com.crypto.arbitrage.repository.FundingSettlementRepository;
import com.crypto.arbitrage.repository.TickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read API for settled (paid) funding prints — historical truth.
 */
@RestController
@RequestMapping("/api/funding-settlements")
@RequiredArgsConstructor
public class FundingSettlementController {

    private final FundingSettlementRepository settlementRepository;
    private final TickerRepository tickerRepository;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "48") int hours) {
        return tickerRepository.findByNormalizedSymbol(symbol)
                .map(ticker -> {
                    LocalDateTime end = LocalDateTime.now();
                    LocalDateTime start = end.minusHours(hours);
                    List<FundingSettlement> rows =
                            settlementRepository.findByTickerBetween(ticker, start, end);
                    List<Map<String, Object>> body = rows.stream().map(s -> {
                        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                        m.put("dex", s.getDexName());
                        m.put("symbol", s.getSymbol());
                        m.put("settlementTime", s.getSettlementTime());
                        m.put("rate", s.getRate());
                        m.put("rate8h", s.getRate8h());
                        m.put("rateAnnual", s.getRateAnnual());
                        m.put("intervalHours", s.getIntervalHours());
                        m.put("source", s.getSource());
                        m.put("predictedDeltaBps", s.getPredictedDeltaBps());
                        m.put("rateKind", "SETTLED");
                        return m;
                    }).collect(Collectors.toList());
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
