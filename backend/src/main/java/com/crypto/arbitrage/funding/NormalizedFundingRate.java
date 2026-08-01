package com.crypto.arbitrage.funding;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class NormalizedFundingRate {
    /** Canonical hourly decimal. */
    BigDecimal hourly;
    /** 8h rate as percent. */
    BigDecimal rate8hPercent;
    /** Simple APR as percent. */
    BigDecimal rateAnnualPercent;
    int intervalHours;
}
