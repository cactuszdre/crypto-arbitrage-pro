package com.crypto.funding.business;

import com.crypto.funding.dto.DexCreateRequest;
import com.crypto.funding.dto.DexDTO;
import com.crypto.funding.exception.ResourceNotFoundException;
import com.crypto.funding.mapper.DexMapper;
import com.crypto.funding.model.Dex;
import com.crypto.funding.repository.DexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for DEX management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DexBusiness {

    private final DexRepository dexRepository;
    private final DexMapper dexMapper;

    /**
     * Get all DEXes
     */
    public List<DexDTO> getAllDexes() {
        return dexMapper.toDTOList(dexRepository.findAll());
    }

    /**
     * Get only active DEXes
     */
    public List<DexDTO> getActiveDexes() {
        return dexMapper.toDTOList(dexRepository.findByActiveTrue());
    }

    /**
     * Get DEX by ID
     */
    public DexDTO getDexById(Long id) {
        Dex dex = dexRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "id", id));
        return dexMapper.toDTO(dex);
    }

    /**
     * Get DEX by name
     */
    public DexDTO getDexByName(String name) {
        Dex dex = dexRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "name", name));
        return dexMapper.toDTO(dex);
    }

    /**
     * Create a new DEX
     */
    @Transactional
    public DexDTO createDex(DexCreateRequest request) {
        log.info("Creating new DEX: {}", request.getName());
        Dex dex = dexMapper.toEntity(request);
        Dex saved = dexRepository.save(dex);
        return dexMapper.toDTO(saved);
    }

    /**
     * Update an existing DEX
     */
    @Transactional
    public DexDTO updateDex(Long id, DexCreateRequest request) {
        log.info("Updating DEX with id: {}", id);
        Dex dex = dexRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DEX", "id", id));
        dexMapper.updateEntity(request, dex);
        Dex saved = dexRepository.save(dex);
        return dexMapper.toDTO(saved);
    }

    /**
     * Delete a DEX
     */
    @Transactional
    public void deleteDex(Long id) {
        log.info("Deleting DEX with id: {}", id);
        if (!dexRepository.existsById(id)) {
            throw new ResourceNotFoundException("DEX", "id", id);
        }
        dexRepository.deleteById(id);
    }

    /**
     * Search DEXes by name (partial match)
     */
    public List<DexDTO> searchByName(String query) {
        return dexRepository.findAll().stream()
                .filter(dex -> dex.getName().toLowerCase().contains(query.toLowerCase()))
                .map(dexMapper::toDTO)
                .toList();
    }
}
