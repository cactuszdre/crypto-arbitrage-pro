package com.crypto.arbitrage.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettledFundingSchedulerTest {

    @Test
    void hourlyAlwaysBoundary() {
        assertTrue(SettledFundingScheduler.isIntervalBoundary(
                LocalDateTime.of(2026, 8, 1, 13, 0), 1));
    }

    @Test
    void eightHourOnlyOnAlignedEpochHours() {
        // Unix epoch hour for 2026-08-01 00:00 UTC should be divisible by 8
        LocalDateTime midnight = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime eight = LocalDateTime.of(2026, 8, 1, 8, 0);
        LocalDateTime one = LocalDateTime.of(2026, 8, 1, 1, 0);

        assertTrue(SettledFundingScheduler.isIntervalBoundary(midnight, 8));
        assertTrue(SettledFundingScheduler.isIntervalBoundary(eight, 8));
        assertFalse(SettledFundingScheduler.isIntervalBoundary(one, 8));
    }
}
