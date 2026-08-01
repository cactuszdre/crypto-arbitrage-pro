package com.crypto.arbitrage.funding;

/**
 * How a venue accrues / pays funding.
 */
public enum FundingModel {
    /** Discrete TWAP paid at interval end; history API available. */
    DISCRETE_TWAP,
    /** Continuous funding index accrual (Paradex). */
    CONTINUOUS_INDEX,
    /** No history API — capture last predicted near settlement as best effort. */
    PREDICTED_SNAPSHOT
}
