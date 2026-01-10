package com.crypto.funding.business;

import com.crypto.funding.model.FundingRate;
import java.util.List;

/**
 * Interface for DEX funding rate fetchers.
 * Each DEX service that fetches funding rates should implement this interface.
 * This allows for dynamic registration and iteration over all DEX fetchers.
 */
public interface DexFetcher {

    /**
     * Get the name of this DEX as stored in the database.
     * @return The DEX name (e.g., "Hyperliquid", "Paradex", "Lighter")
     */
    String getDexName();

    /**
     * Fetch funding rate data from this DEX's API.
     * @return List of FundingRate objects, or empty list if fetch fails
     */
    List<FundingRate> fetchFundingData();

    /**
     * Check if this fetcher is enabled (DEX exists and is active in DB).
     * @return true if this DEX should be fetched, false otherwise
     */
    default boolean isEnabled() {
        return true; // Default to enabled, can be overridden
    }
}
