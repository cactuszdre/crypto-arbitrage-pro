-- =====================================================
-- Flyway Migration: Create funding_rate_hourly table
-- Description: Hourly aggregated funding rates for 1D, 3D, 7D charts
-- Author: Automated
-- =====================================================

CREATE TABLE IF NOT EXISTS funding_rate_hourly (
    id BIGSERIAL PRIMARY KEY,
    ticker_id INTEGER NOT NULL REFERENCES ticker(id),
    dex_id BIGINT NOT NULL REFERENCES dex(id),
    bucket_time TIMESTAMP NOT NULL,
    avg_rate DECIMAL(20, 12) NOT NULL,
    min_rate DECIMAL(20, 12),
    max_rate DECIMAL(20, 12),
    avg_rate_8h DECIMAL(20, 12),
    avg_rate_annual DECIMAL(20, 4),
    open_rate DECIMAL(20, 12),
    close_rate DECIMAL(20, 12),
    sample_count INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_funding_hourly_ticker_dex_bucket UNIQUE (ticker_id, dex_id, bucket_time)
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_funding_hourly_bucket_time ON funding_rate_hourly(bucket_time);
CREATE INDEX IF NOT EXISTS idx_funding_hourly_ticker_bucket ON funding_rate_hourly(ticker_id, bucket_time);
CREATE INDEX IF NOT EXISTS idx_funding_hourly_dex_bucket ON funding_rate_hourly(dex_id, bucket_time);
CREATE INDEX IF NOT EXISTS idx_funding_hourly_ticker_dex_bucket ON funding_rate_hourly(ticker_id, dex_id, bucket_time);

-- Comment on table
COMMENT ON TABLE funding_rate_hourly IS 'Hourly aggregated funding rate data for 1D, 3D, 7D chart display. Retained for 7 days.';
COMMENT ON COLUMN funding_rate_hourly.bucket_time IS 'End time of the 1-hour bucket (e.g., 10:00 means data from 09:00-10:00)';
COMMENT ON COLUMN funding_rate_hourly.sample_count IS 'Number of raw 10-second samples aggregated in this hour';
