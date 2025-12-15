package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.dto.FundingRateChartDTO;
import com.crypto.arbitrage.service.FundingRateChartService;
import com.crypto.arbitrage.service.FundingRate5MinAggregationService;
import com.crypto.arbitrage.service.FundingRateHourlyAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for chart data endpoints.
 * Provides pre-aggregated data optimized for real-time chart display.
 * - 5-minute data for Live/24H charts
 * - Hourly data for 1D, 3D, 7D charts  
 * - Daily data for 30D+ charts
 */
@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartController {

    private final FundingRateChartService chartService;
    private final FundingRate5MinAggregationService aggregationService;
    private final FundingRateHourlyAggregationService hourlyAggregationService;

    /**
     * Get chart data for a specific symbol and DEX
     * 
     * @param symbol Normalized symbol (e.g., "BTC", "ETH")
     * @param dex DEX name (e.g., "Paradex", "Hyperliquid")
     * @param hours Number of hours of data (default: 24)
     */
    @GetMapping("/funding-rate/{symbol}/{dex}")
    public ResponseEntity<List<FundingRateChartDTO>> getChartData(
            @PathVariable String symbol,
            @PathVariable String dex,
            @RequestParam(defaultValue = "24") int hours) {
        
        List<FundingRateChartDTO> data = chartService.getChartData(symbol, dex, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get chart data for a symbol across all DEXs
     * Useful for comparison charts
     * 
     * @param symbol Normalized symbol
     * @param hours Number of hours
     */
    @GetMapping("/funding-rate/{symbol}")
    public ResponseEntity<List<FundingRateChartDTO>> getChartDataBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        
        List<FundingRateChartDTO> data = chartService.getChartDataBySymbol(symbol, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get chart data for all symbols on a specific DEX
     * 
     * @param dex DEX name
     * @param hours Number of hours
     */
    @GetMapping("/funding-rate/dex/{dex}")
    public ResponseEntity<List<FundingRateChartDTO>> getChartDataByDex(
            @PathVariable String dex,
            @RequestParam(defaultValue = "24") int hours) {
        
        List<FundingRateChartDTO> data = chartService.getChartDataByDex(dex, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get the latest N data points for a symbol/DEX
     * Optimized for real-time updates
     * 
     * @param symbol Normalized symbol
     * @param dex DEX name
     * @param count Number of points (default: 12 = 1 hour of 5-min buckets)
     */
    @GetMapping("/funding-rate/{symbol}/{dex}/latest")
    public ResponseEntity<List<FundingRateChartDTO>> getLatestDataPoints(
            @PathVariable String symbol,
            @PathVariable String dex,
            @RequestParam(defaultValue = "12") int count) {
        
        List<FundingRateChartDTO> data = chartService.getLatestDataPoints(symbol, dex, count);
        return ResponseEntity.ok(data);
    }

    /**
     * Get comparison data for a symbol across all DEXs, grouped by DEX
     * Perfect for multi-series line charts
     * 
     * @param symbol Symbol to compare
     * @param hours Number of hours
     */
    @GetMapping("/funding-rate/{symbol}/compare")
    public ResponseEntity<Map<String, List<FundingRateChartDTO>>> getComparisonData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        
        Map<String, List<FundingRateChartDTO>> data = chartService.getComparisonData(symbol, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get statistics summary for a symbol
     * 
     * @param symbol Symbol
     * @param hours Hours for calculation
     */
    @GetMapping("/funding-rate/{symbol}/stats")
    public ResponseEntity<FundingRateChartService.ChartStatsSummary> getStatsSummary(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        
        FundingRateChartService.ChartStatsSummary stats = chartService.getStatsSummary(symbol, hours);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get daily comparison data for a symbol across all DEXs, grouped by DEX
     * Uses the funding_rate_daily table for historical data (30D+ views)
     * 
     * @param symbol Symbol to compare
     * @param days Number of days (default: 30)
     */
    @GetMapping("/funding-rate/{symbol}/compare-daily")
    public ResponseEntity<Map<String, List<FundingRateChartDTO>>> getDailyComparisonData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days) {
        
        Map<String, List<FundingRateChartDTO>> data = chartService.getDailyComparisonData(symbol, days);
        return ResponseEntity.ok(data);
    }

    /**
     * Get hourly comparison data for a symbol across all DEXs, grouped by DEX
     * Uses the funding_rate_hourly table for medium-term data (1D, 3D, 7D views)
     * 
     * @param symbol Symbol to compare
     * @param hours Number of hours (24 for 1D, 72 for 3D, 168 for 7D)
     */
    @GetMapping("/funding-rate/{symbol}/compare-hourly")
    public ResponseEntity<Map<String, List<FundingRateChartDTO>>> getHourlyComparisonData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        
        Map<String, List<FundingRateChartDTO>> data = chartService.getHourlyComparisonData(symbol, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get aggregation statistics (admin endpoint)
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<String> getAggregationStats() {
        return ResponseEntity.ok(aggregationService.getAggregationStats());
    }

    /**
     * Trigger manual 5-minute aggregation (admin endpoint)
     */
    @PostMapping("/admin/aggregate")
    public ResponseEntity<String> triggerAggregation() {
        aggregationService.manualAggregate();
        return ResponseEntity.ok("5-minute aggregation triggered");
    }

    /**
     * Get hourly aggregation statistics (admin endpoint)
     */
    @GetMapping("/admin/stats-hourly")
    public ResponseEntity<String> getHourlyAggregationStats() {
        return ResponseEntity.ok(hourlyAggregationService.getAggregationStats());
    }
}
