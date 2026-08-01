package com.crypto.arbitrage.funding;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MarketScheduleSnapshot {
    int intervalHours;
    LocalDateTime nextFundingTime;
    boolean continuous;
}
