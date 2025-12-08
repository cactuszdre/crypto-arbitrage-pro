package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.CryptoPriceDTO;
import com.crypto.arbitrage.service.CryptoPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crypto-prices")
@RequiredArgsConstructor
public class CryptoPriceController {

    private final CryptoPriceService cryptoPriceService;

    @GetMapping
    public ResponseEntity<List<CryptoPriceDTO>> getAllPrices() {
        return ResponseEntity.ok(cryptoPriceService.getCachedPrices());
    }
}
