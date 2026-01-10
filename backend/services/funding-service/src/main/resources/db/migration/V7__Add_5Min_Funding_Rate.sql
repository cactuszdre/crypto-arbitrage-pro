-- Migration V7: Add 5-minute aggregated funding rates table for charts
-- This table stores 5-minute averages of funding rates optimized for real-time chart display

CREATE TABLE funding_rate_5min (
    id BIGSERIAL PRIMARY KEY,
    ticker_id INT NOT NULL,
    dex_id BIGINT NOT NULL,
    bucket_time TIMESTAMP NOT NULL,
    avg_rate DECIMAL(20, 12) NOT NULL,
    min_rate DECIMAL(20, 12),
    max_rate DECIMAL(20, 12),
    avg_rate_8h DECIMAL(20, 12),
    avg_rate_annual DECIMAL(20, 4),
    open_rate DECIMAL(20, 12),
    close_rate DECIMAL(20, 12),
    sample_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (ticker_id, dex_id, bucket_time),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id),
    FOREIGN KEY (dex_id) REFERENCES dex(id)
);

-- Indexes optimized for chart queries
CREATE INDEX idx_funding_5min_bucket_time ON funding_rate_5min (bucket_time);
CREATE INDEX idx_funding_5min_ticker_bucket ON funding_rate_5min (ticker_id, bucket_time);
CREATE INDEX idx_funding_5min_dex_bucket ON funding_rate_5min (dex_id, bucket_time);
CREATE INDEX idx_funding_5min_ticker_dex_bucket ON funding_rate_5min (ticker_id, dex_id, bucket_time);
