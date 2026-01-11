package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.DexDTO;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.EncryptionService;
import com.crypto.arbitrage.service.MarketSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dex")
@RequiredArgsConstructor
public class DexController {

    private final DexRepository dexRepository;
    private final EncryptionService encryptionService;
    private final MarketSyncService marketSyncService;

    @GetMapping
    public ResponseEntity<List<DexDTO>> getAllDex() {
        List<DexDTO> dexList = dexRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dexList);
    }

    @GetMapping("/active")
    public ResponseEntity<List<DexDTO>> getActiveDex() {
        List<DexDTO> dexList = dexRepository.findByActiveTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dexList);
    }

    @GetMapping("/{name}")
    public ResponseEntity<DexDTO> getDexByName(@PathVariable String name) {
        return dexRepository.findByName(name)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dex> createDex(@RequestBody DexDTO dexDTO) {
        Dex dex = new Dex();
        dex.setName(dexDTO.getName());
        dex.setBaseUrl(dexDTO.getBaseUrl());
        // Encrypt API Key if present
        if (dexDTO.getApiKey() != null && !dexDTO.getApiKey().isEmpty()) {
            dex.setApiKey(encryptionService.encrypt(dexDTO.getApiKey()));
        }
        // Default values for now
        dex.setFundingIntervalHours(8);
        dex.setActive(true);
        dex.setCreatedAt(LocalDateTime.now());
        
        return ResponseEntity.ok(dexRepository.save(dex));
    }

    /**
     * Manually trigger market synchronization for all DEXes.
     * This will:
     * 1. Fetch current available symbols from each DEX
     * 2. Deactivate markets that are no longer available (e.g., delisted pairs like FLOW on Paradex)
     * 3. Delete corresponding LastFunding entries for delisted pairs
     * 4. Add any new markets
     *
     * @return Status message
     */
    @PostMapping("/sync-markets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncMarkets() {
        marketSyncService.manualSync();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Market sync completed. Delisted pairs have been deactivated and removed from arbitrage."
        ));
    }

    /**
     * Reactivate all markets for a specific DEX.
     * Useful for recovery after accidental mass deactivation due to API errors.
     *
     * @param dexName Name of the DEX to reactivate markets for
     * @return Number of markets reactivated
     */
    @PostMapping("/{dexName}/reactivate-markets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reactivateMarkets(@PathVariable String dexName) {
        int count = marketSyncService.reactivateAllMarkets(dexName);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "dex", dexName,
            "marketsReactivated", count,
            "message", String.format("Reactivated %d markets for %s", count, dexName)
        ));
    }

    private DexDTO toDTO(Dex dex) {
        return DexDTO.builder()
                .id(dex.getId())
                .name(dex.getName())
                .baseUrl(dex.getBaseUrl())
                .fundingIntervalHours(dex.getFundingIntervalHours())
                .active(dex.getActive())
                .color(dex.getColor())
                .iconUrl(dex.getIconUrl())
                .tradeUrlPattern(dex.getTradeUrlPattern())
                .createdAt(dex.getCreatedAt())
                // Do not return API key
                .build();
    }
}

