package com.crypto.arbitrage.funding;

import com.crypto.arbitrage.model.FundingSettlement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DEX adapters that can load settled (paid) funding prints and market schedules.
 */
public interface SettledFundingProvider {

    String getDexName();

    FundingModel getFundingModel();

    /**
     * Whether the venue exposes a reliable post-settlement history API.
     */
    default boolean supportsSettledHistory() {
        return getFundingModel() != FundingModel.PREDICTED_SNAPSHOT;
    }

    /**
     * Fetch settled prints for one market between {@code from} (inclusive) and {@code to} (exclusive).
     */
    List<FundingSettlement> fetchSettledHistory(String normalizedSymbol, Instant from, Instant to);

    /**
     * Optional schedule hint for one market (interval + next funding time).
     */
    default Optional<MarketScheduleSnapshot> fetchSchedule(String normalizedSymbol) {
        return Optional.empty();
    }

    /**
     * Sync schedules for all known markets on this DEX (batch when possible).
     * @return number of schedules upserted
     */
    default int syncAllSchedules() {
        return 0;
    }
}
