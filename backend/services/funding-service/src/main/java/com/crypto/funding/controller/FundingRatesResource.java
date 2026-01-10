package com.crypto.funding.controller;

import com.crypto.funding.dto.FundingRateDTO;
import com.crypto.funding.dto.MarketSymbolDTO;
import com.crypto.funding.business.FundingRateService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JAX-RS Resource for Funding Rate endpoints.
 * Compatible with the old /api/funding-rates paths for backwards compatibility.
 */
@Component
@Path("/funding-rates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class FundingRatesResource {

    private final FundingRateService fundingRateService;

    /**
     * Get all latest funding rates
     */
    @GET
    public List<FundingRateDTO> getAllRates() {
        return fundingRateService.getAllLatestRates();
    }

    /**
     * Get rates by DEX
     */
    @GET
    @Path("/dex/{dexName}")
    public List<FundingRateDTO> getRatesByDex(@PathParam("dexName") String dexName) {
        return fundingRateService.getRatesByDex(dexName);
    }

    /**
     * Get latest rate for specific DEX and symbol
     */
    @GET
    @Path("/dex/{dexName}/symbol/{symbol}")
    public Response getLatestRate(
            @PathParam("dexName") String dexName,
            @PathParam("symbol") String symbol) {
        return fundingRateService.getLatestRate(dexName, symbol)
                .map(dto -> Response.ok(dto).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Get funding rate history
     */
    @GET
    @Path("/history")
    public List<FundingRateDTO> getHistory(
            @QueryParam("dex") String dex,
            @QueryParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return fundingRateService.getHistory(dex, symbol, hours);
    }

    /**
     * Get symbols for a DEX
     */
    @GET
    @Path("/symbols/{dexName}")
    public List<String> getSymbolsByDex(@PathParam("dexName") String dexName) {
        return fundingRateService.getSymbolsByDex(dexName);
    }

    /**
     * Get all symbols with icons
     */
    @GET
    @Path("/symbols")
    public List<MarketSymbolDTO> getAllSymbols() {
        return fundingRateService.getAllMonitoredSymbolsWithIcons();
    }
}
