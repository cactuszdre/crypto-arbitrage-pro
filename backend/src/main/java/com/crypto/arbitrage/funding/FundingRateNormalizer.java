package com.crypto.arbitrage.funding;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared conversion of venue-native funding rates into canonical hourly / 8h% / APR%.
 */
public final class FundingRateNormalizer {

    private FundingRateNormalizer() {
    }

    public static NormalizedFundingRate normalize(BigDecimal raw, FundingRateUnit unit, int intervalHours) {
        if (raw == null) {
            throw new IllegalArgumentException("raw rate is null");
        }
        int hours = Math.max(1, intervalHours);

        BigDecimal hourly = switch (unit) {
            case HOURLY -> raw;
            case EIGHT_HOUR -> raw.divide(BigDecimal.valueOf(8), 18, RoundingMode.HALF_UP);
            case DAILY -> raw.divide(BigDecimal.valueOf(24), 18, RoundingMode.HALF_UP);
            case ANNUAL -> raw.divide(BigDecimal.valueOf(24L * 365L), 18, RoundingMode.HALF_UP);
            case PERIOD -> raw.divide(BigDecimal.valueOf(hours), 18, RoundingMode.HALF_UP);
        };

        BigDecimal rate8hPercent = hourly.multiply(BigDecimal.valueOf(8))
                .multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP);

        BigDecimal rateAnnualPercent = hourly.multiply(BigDecimal.valueOf(24L * 365L))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return NormalizedFundingRate.builder()
                .hourly(hourly)
                .rate8hPercent(rate8hPercent)
                .rateAnnualPercent(rateAnnualPercent)
                .intervalHours(hours)
                .build();
    }

    /**
     * When the API already returns APR as a percent (e.g. 10.95), not a decimal.
     */
    public static NormalizedFundingRate fromAnnualPercent(BigDecimal aprPercent, int intervalHours) {
        BigDecimal annualDecimal = aprPercent.divide(BigDecimal.valueOf(100), 18, RoundingMode.HALF_UP);
        return normalize(annualDecimal, FundingRateUnit.ANNUAL, intervalHours);
    }
}
