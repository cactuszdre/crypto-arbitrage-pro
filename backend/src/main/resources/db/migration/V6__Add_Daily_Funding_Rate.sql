-- Migration V6: Add daily aggregated funding rates table
-- This table stores daily averages of funding rates to reduce storage while keeping historical data

CREATE TABLE funding_rate_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    
    UNIQUE KEY uk_ticker_dex_date (ticker_id, dex_id, date),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id),
    FOREIGN KEY (dex_id) REFERENCES dex(id),
    INDEX idx_date (date),
    INDEX idx_ticker_date (ticker_id, date)
);
