package com.crypto.arbitrage.funding;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundingRateNormalizerTest {

    @Test
    void normalizesHourly() {
        NormalizedFundingRate n = FundingRateNormalizer.normalize(
                new BigDecimal("0.0001"), FundingRateUnit.HOURLY, 1);
        assertEquals(new BigDecimal("0.0001"), n.getHourly());
        assertEquals(0, new BigDecimal("0.080000").compareTo(n.getRate8hPercent()));
        assertEquals(0, new BigDecimal("87.60").compareTo(n.getRateAnnualPercent()));
    }

    @Test
    void normalizesEightHourQuote() {
        NormalizedFundingRate n = FundingRateNormalizer.normalize(
                new BigDecimal("0.0008"), FundingRateUnit.EIGHT_HOUR, 1);
        assertEquals(0, new BigDecimal("0.000100000000000000").compareTo(n.getHourly()));
    }

    @Test
    void normalizesDaily() {
        NormalizedFundingRate n = FundingRateNormalizer.normalize(
                new BigDecimal("0.0024"), FundingRateUnit.DAILY, 1);
        assertEquals(0, new BigDecimal("0.000100000000000000").compareTo(n.getHourly()));
    }

    @Test
    void normalizesPeriod() {
        NormalizedFundingRate n = FundingRateNormalizer.normalize(
                new BigDecimal("0.0008"), FundingRateUnit.PERIOD, 8);
        assertEquals(0, new BigDecimal("0.000100000000000000").compareTo(n.getHourly()));
    }
}
