package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.FundingRateChartDTO;
import com.crypto.arbitrage.business.FundingRateChartService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JAX-RS Resource for chart data endpoints.
 * Provides pre-aggregated data optimized for real-time chart display.
 */
@Component
@Path("/charts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ChartResource {

    private final FundingRateChartService chartService;

    /**
     * Get chart data for a specific symbol and DEX
     */
    @GET
    @Path("/funding-rate/{symbol}/{dex}")
    public List<FundingRateChartDTO> getChartData(
            @PathParam("symbol") String symbol,
            @PathParam("dex") String dex,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getChartData(symbol, dex, hours);
    }

    /**
     * Get chart data for a symbol across all DEXs
     */
    @GET
    @Path("/funding-rate/{symbol}")
    public List<FundingRateChartDTO> getChartDataBySymbol(
            @PathParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getChartDataBySymbol(symbol, hours);
    }

    /**
     * Get chart data for all symbols on a specific DEX
     */
    @GET
    @Path("/funding-rate/dex/{dex}")
    public List<FundingRateChartDTO> getChartDataByDex(
            @PathParam("dex") String dex,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getChartDataByDex(dex, hours);
    }

    /**
     * Get the latest N data points for a symbol/DEX
     */
    @GET
    @Path("/funding-rate/{symbol}/{dex}/latest")
    public List<FundingRateChartDTO> getLatestDataPoints(
            @PathParam("symbol") String symbol,
            @PathParam("dex") String dex,
            @QueryParam("count") @DefaultValue("12") int count) {
        return chartService.getLatestDataPoints(symbol, dex, count);
    }

    /**
     * Get comparison data for a symbol across all DEXs
     */
    @GET
    @Path("/funding-rate/{symbol}/compare")
    public Map<String, List<FundingRateChartDTO>> getComparisonData(
            @PathParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getComparisonData(symbol, hours);
    }

    /**
     * Get daily comparison data for historical charts (30D+)
     */
    @GET
    @Path("/funding-rate/{symbol}/compare-daily")
    public Map<String, List<FundingRateChartDTO>> getDailyComparisonData(
            @PathParam("symbol") String symbol,
            @QueryParam("days") @DefaultValue("30") int days) {
        return chartService.getDailyComparisonData(symbol, days);
    }

    /**
     * Get hourly comparison data for medium-term charts (1D, 3D, 7D)
     */
    @GET
    @Path("/funding-rate/{symbol}/compare-hourly")
    public Map<String, List<FundingRateChartDTO>> getHourlyComparisonData(
            @PathParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getHourlyComparisonData(symbol, hours);
    }

    /**
     * Get statistics summary for a symbol
     */
    @GET
    @Path("/funding-rate/{symbol}/stats")
    public FundingRateChartService.ChartStatsSummary getStatsSummary(
            @PathParam("symbol") String symbol,
            @QueryParam("hours") @DefaultValue("24") int hours) {
        return chartService.getStatsSummary(symbol, hours);
    }
}
