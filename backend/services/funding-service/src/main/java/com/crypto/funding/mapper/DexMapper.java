package com.crypto.funding.mapper;

import com.crypto.funding.dto.DexDTO;
import com.crypto.funding.dto.DexCreateRequest;
import com.crypto.funding.model.Dex;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * MapStruct mapper for Dex entity/DTO conversions.
 */
@Mapper(componentModel = "spring")
public interface DexMapper {
    
    /**
     * Convert Dex entity to DTO (excludes apiKey for security)
     */
    DexDTO toDTO(Dex dex);
    
    /**
     * Convert list of Dex entities to DTOs
     */
    List<DexDTO> toDTOList(List<Dex> dexes);
    
    /**
     * Convert create request to entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Dex toEntity(DexCreateRequest request);
    
    /**
     * Update existing entity from request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(DexCreateRequest request, @MappingTarget Dex dex);
}
