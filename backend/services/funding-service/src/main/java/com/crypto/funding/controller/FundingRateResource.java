package com.crypto.funding.controller;

import com.crypto.funding.business.FundingRateBusiness;
import com.crypto.funding.dto.FundingRateDTO;
import com.crypto.funding.dto.MarketSymbolDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JAX-RS Resource for Funding Rate operations.
 * Provides read operations for funding rate data.
 */
@Component
@Path("/funding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class FundingRateResource {

    private final FundingRateBusiness fundingRateBusiness;

    /**
     * Get all latest funding rates
     */
    @GET
    @Path("/rates")
    public List<FundingRateDTO> getAllLatestRates() {
        return fundingRateBusiness.getAllLatestRates();
    }

    /**
     * Get funding rates by DEX
     */
    @GET
    @Path("/rates/dex/{dexName}")
    public List<FundingRateDTO> getRatesByDex(@PathParam("dexName") String dexName) {
        return fundingRateBusiness.getRatesByDex(dexName);
    }

    /**
     * Get latest rate for a specific symbol on a DEX
     */
    @GET
    @Path("/rates/dex/{dexName}/symbol/{symbol}")
    public FundingRateDTO getLatestRate(
            @PathParam("dexName") String dexName,
            @PathParam("symbol") String symbol) {
        return fundingRateBusiness.getLatestRate(dexName, symbol)
                .orElseThrow(() -> new NotFoundException(
                        "No funding rate found for " + symbol + " on " + dexName));
    }

    /**
     * Get funding rate history
     */
    @GET
    @Path("/rates/history/{dexName}/{symbol}")
    public List<FundingRateDTO> getHistory(
            @PathParam("dexName") String dexName,
            @PathParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return fundingRateBusiness.getHistory(dexName, symbol, hours);
    }

    /**
     * Get all symbols for a DEX
     */
    @GET
    @Path("/symbols/dex/{dexName}")
    public List<String> getSymbolsByDex(@PathParam("dexName") String dexName) {
        return fundingRateBusiness.getSymbolsByDex(dexName);
    }

    /**
     * Get all monitored symbols
     */
    @GET
    @Path("/symbols")
    public List<String> getAllMonitoredSymbols() {
        return fundingRateBusiness.getAllMonitoredSymbols();
    }

    /**
     * Get all monitored symbols with icons
     */
    @GET
    @Path("/symbols/icons")
    public List<MarketSymbolDTO> getAllMonitoredSymbolsWithIcons() {
        return fundingRateBusiness.getAllMonitoredSymbolsWithIcons();
    }

    /**
     * Search funding rates with filters
     */
    @GET
    @Path("/rates/search")
    public List<FundingRateDTO> searchRates(
            @QueryParam("dex") String dexName,
            @QueryParam("symbol") String symbol,
            @QueryParam("hours") Integer hours) {
        return fundingRateBusiness.searchRates(dexName, symbol, hours);
    }
}
