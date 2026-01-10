-- Migration V6: Add daily aggregated funding rates table
-- This table stores daily averages of funding rates to reduce storage while keeping historical data

CREATE TABLE funding_rate_daily (
    id BIGSERIAL PRIMARY KEY,
    ticker_id INT NOT NULL,
    dex_id BIGINT NOT NULL,
    date DATE NOT NULL,
    avg_rate DECIMAL(20, 12) NOT NULL,
    min_rate DECIMAL(20, 12),
    max_rate DECIMAL(20, 12),
    avg_rate_8h DECIMAL(20, 12),
    avg_rate_annual DECIMAL(20, 4),
    sample_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (ticker_id, dex_id, date),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id),
    FOREIGN KEY (dex_id) REFERENCES dex(id)
);

CREATE INDEX idx_funding_daily_date ON funding_rate_daily (date);
CREATE INDEX idx_funding_daily_ticker_date ON funding_rate_daily (ticker_id, date);
