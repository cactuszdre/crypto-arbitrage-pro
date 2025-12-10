-- Migration V8: Add last_funding table for optimized latest rate lookups
-- This table maintains only the most recent funding rate per ticker/DEX combination
-- Used for dashboard stats cards and arbitrage calculations

CREATE TABLE last_funding (
    id BIGSERIAL PRIMARY KEY,
    ticker_id INT NOT NULL,
    dex_id BIGINT NOT NULL,
    rate DECIMAL(20, 12) NOT NULL,
    rate_8h DECIMAL(20, 12),
    rate_annual DECIMAL(20, 4),
    funding_premium DECIMAL(20, 8),
    funding_index DECIMAL(30, 12),
    interval_hours INT NOT NULL DEFAULT 1,
    timestamp TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (ticker_id, dex_id),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id),
    FOREIGN KEY (dex_id) REFERENCES dex(id)
);

-- Indexes for common query patterns
CREATE INDEX idx_last_funding_ticker ON last_funding (ticker_id);
CREATE INDEX idx_last_funding_dex ON last_funding (dex_id);
CREATE INDEX idx_last_funding_rate_annual ON last_funding (rate_annual DESC);
CREATE INDEX idx_last_funding_timestamp ON last_funding (timestamp DESC);

-- Populate initial data from existing funding_rate table
INSERT INTO last_funding (ticker_id, dex_id, rate, rate_8h, rate_annual, funding_premium, funding_index, interval_hours, timestamp)
SELECT DISTINCT ON (f.ticker_id, f.dex_id)
    f.ticker_id,
    f.dex_id,
    f.rate,
    f.rate_8h,
    f.rate_annual,
    f.funding_premium,
    f.funding_index,
    f.interval_hours,
    f.timestamp
FROM funding_rate f
ORDER BY f.ticker_id, f.dex_id, f.timestamp DESC;
