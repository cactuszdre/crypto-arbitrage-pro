package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.ArbitrageOpportunityDTO;
import com.crypto.arbitrage.dto.HistoricalArbitrageResponse;
import com.crypto.arbitrage.dto.HistoricalRatesResponse;
import com.crypto.arbitrage.dto.SymbolRatesDTO;
import com.crypto.arbitrage.business.ArbitrageService;
import com.crypto.arbitrage.business.HistoricalArbitrageService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JAX-RS Resource for Arbitrage endpoints.
 * Hosted on Arbitrage Service (Port 8082).
 */
@Component
@Path("/arbitrage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ArbitrageResource {

    private final ArbitrageService arbitrageService;
    private final HistoricalArbitrageService historicalArbitrageService;

    /**
     * Get all current arbitrage opportunities
     */
    @GET
    @Path("/opportunities")
    public List<ArbitrageOpportunityDTO> getOpportunities() {
        return arbitrageService.getArbitrageOpportunities();
    }

    /**
     * Get all rates grouped by symbol
     */
    @GET
    @Path("/rates/all")
    public List<SymbolRatesDTO> getAllRates() {
        return arbitrageService.getAllRatesBySymbol();
    }

    /**
     * Get historical arbitrage opportunities
     */
    @GET
    @Path("/rates/historical")
    public HistoricalRatesResponse getHistoricalRates(
            @QueryParam("days") @DefaultValue("7") int days) {
        return historicalArbitrageService.getAllHistoricalRatesBySymbol(days);
    }

    /**
     * Get historical arbitrage opportunities
     */
    @GET
    @Path("/opportunities/historical")
    public HistoricalArbitrageResponse getHistoricalOpportunities(
            @QueryParam("days") @DefaultValue("7") int days) {
        return historicalArbitrageService.getHistoricalOpportunities(days);
    }

    /**
     * Health check endpoint
     */
    @GET
    @Path("/health")
    public String health() {
        return "{\"status\": \"UP\", \"service\": \"arbitrage-service\"}";
    }
}
