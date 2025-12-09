-- Migration V5: Add centralized ticker table
-- This migration creates a ticker table and refactors other tables to use ticker_id

-- 1. Create ticker table
CREATE TABLE ticker (
    id SERIAL PRIMARY KEY,
    base_symbol VARCHAR(20) NOT NULL,
    normalized_symbol VARCHAR(50) NOT NULL UNIQUE,
    icon_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ticker_base_symbol ON ticker (base_symbol);
CREATE INDEX idx_ticker_normalized_symbol ON ticker (normalized_symbol);

-- 2. Migrate dex_market to reference ticker (add nullable column first)
ALTER TABLE dex_market ADD COLUMN ticker_id INT NULL;

-- 3. Drop and recreate funding_rate with ticker_id
DROP TABLE IF EXISTS funding_rate;
CREATE TABLE funding_rate (
    id BIGSERIAL PRIMARY KEY,
    dex_id BIGINT NOT NULL,
    ticker_id INT NOT NULL,
    rate DECIMAL(20, 12) NOT NULL,
    rate_8h DECIMAL(20, 12),
    rate_annual DECIMAL(20, 4),
    funding_premium DECIMAL(20, 8),
    funding_index DECIMAL(30, 12),
    interval_hours INT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dex_id) REFERENCES dex(id),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id)
);

CREATE INDEX idx_funding_dex_ticker_time ON funding_rate (dex_id, ticker_id, timestamp);
CREATE INDEX idx_funding_rate_timestamp ON funding_rate (timestamp);

-- 4. Drop and recreate arbitrage_opportunity with ticker_id
DROP TABLE IF EXISTS arbitrage_opportunity;
CREATE TABLE arbitrage_opportunity (
    id BIGSERIAL PRIMARY KEY,
    dex1_id BIGINT NOT NULL,
    dex2_id BIGINT NOT NULL,
    ticker_id INT NOT NULL,
    dex1_rate DECIMAL(20, 12) NOT NULL,
    dex2_rate DECIMAL(20, 12) NOT NULL,
    spread DECIMAL(20, 12) NOT NULL,
    projected_daily_return DECIMAL(20, 8),
    recommended_long_dex VARCHAR(50),
    recommended_short_dex VARCHAR(50),
    detected_at TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    FOREIGN KEY (dex1_id) REFERENCES dex(id),
    FOREIGN KEY (dex2_id) REFERENCES dex(id),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id)
);

CREATE INDEX idx_arb_ticker_time ON arbitrage_opportunity (ticker_id, detected_at);

-- 5. Drop and recreate position with ticker_id
DROP TABLE IF EXISTS position;
CREATE TABLE position (
    id BIGSERIAL PRIMARY KEY,
    dex_id BIGINT NOT NULL,
    ticker_id INT NOT NULL,
    side VARCHAR(10) NOT NULL,
    size DECIMAL(18, 8) NOT NULL,
    entry_price DECIMAL(18, 8),
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    pnl DECIMAL(18, 8),
    status VARCHAR(20) DEFAULT 'OPEN',
    FOREIGN KEY (dex_id) REFERENCES dex(id),
    FOREIGN KEY (ticker_id) REFERENCES ticker(id)
);

-- 6. Add foreign key to dex_market after ticker table exists
ALTER TABLE dex_market
    ADD CONSTRAINT fk_dex_market_ticker FOREIGN KEY (ticker_id) REFERENCES ticker(id);
