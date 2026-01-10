package com.crypto.funding.mapper;

import com.crypto.funding.dto.FundingRateDTO;
import com.crypto.funding.model.FundingRate;
import com.crypto.funding.model.LastFunding;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

/**
 * MapStruct mapper for FundingRate entity/DTO conversions.
 */
@Mapper(componentModel = "spring")
public interface FundingRateMapper {
    
    /**
     * Convert FundingRate entity to DTO
     */
    @Mapping(source = "dex.name", target = "dexName")
    @Mapping(source = "ticker.normalizedSymbol", target = "symbol")
    @Mapping(target = "direction", expression = "java(calculateDirection(fundingRate.getRate()))")
    FundingRateDTO toDTO(FundingRate fundingRate);
    
    /**
     * Convert LastFunding entity to DTO
     */
    @Mapping(source = "dex.name", target = "dexName")
    @Mapping(source = "ticker.normalizedSymbol", target = "symbol")
    @Mapping(target = "direction", expression = "java(calculateDirection(lastFunding.getRate()))")
    FundingRateDTO toDTO(LastFunding lastFunding);
    
    /**
     * Convert list of FundingRate entities to DTOs
     */
    List<FundingRateDTO> toDTOList(List<FundingRate> fundingRates);
    
    /**
     * Convert list of LastFunding entities to DTOs
     */
    List<FundingRateDTO> lastFundingToDTOList(List<LastFunding> lastFundings);
    
    /**
     * Calculate direction based on rate sign
     */
    @Named("calculateDirection")
    default String calculateDirection(BigDecimal rate) {
        if (rate == null) return null;
        return rate.compareTo(BigDecimal.ZERO) > 0 ? "LONGS_PAY" : "SHORTS_PAY";
    }
}
