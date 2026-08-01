package com.crypto.arbitrage.funding;

/**
 * Unit of a raw funding rate as returned by a venue API.
 */
public enum FundingRateUnit {
    /** Rate paid / accruing per hour (decimal). */
    HOURLY,
    /** 8-hour equivalent quote (decimal); convert via /8. */
    EIGHT_HOUR,
    /** 24-hour rate (decimal); convert via /24. */
    DAILY,
    /** Annualized decimal APR (e.g. 0.1095 = 10.95%). */
    ANNUAL,
    /** Rate for one full funding period of {@code intervalHours}. */
    PERIOD
}
