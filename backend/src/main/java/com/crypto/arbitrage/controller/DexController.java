package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.DexDTO;
import com.crypto.arbitrage.model.Dex;
import com.crypto.arbitrage.repository.DexRepository;
import com.crypto.arbitrage.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dex")
@RequiredArgsConstructor
public class DexController {

    private final DexRepository dexRepository;
    private final EncryptionService encryptionService;

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
