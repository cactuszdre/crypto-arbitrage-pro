-- V19: Dual-track funding — settled prints + per-market schedule
-- Predicted (live) stays in funding_rate / last_funding; settled is truth for history/charts.

CREATE TABLE funding_settlement (
    id BIGSERIAL PRIMARY KEY,
    dex_id BIGINT NOT NULL REFERENCES dex(id),
    ticker_id INT NOT NULL REFERENCES ticker(id),
    settlement_time TIMESTAMP NOT NULL,
    interval_hours INT NOT NULL DEFAULT 1,
    rate DECIMAL(20, 12) NOT NULL,
    rate_8h DECIMAL(20, 12),
    rate_annual DECIMAL(20, 4),
    funding_premium DECIMAL(20, 8),
    funding_index DECIMAL(30, 12),
    source VARCHAR(64) NOT NULL,
    predicted_rate DECIMAL(20, 12),
    predicted_delta_bps DECIMAL(20, 6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dex_id, ticker_id, settlement_time)
);

CREATE INDEX idx_funding_settlement_ticker_time ON funding_settlement (ticker_id, settlement_time DESC);
CREATE INDEX idx_funding_settlement_dex_time ON funding_settlement (dex_id, settlement_time DESC);
CREATE INDEX idx_funding_settlement_time ON funding_settlement (settlement_time DESC);

CREATE TABLE market_funding_schedule (
    id BIGSERIAL PRIMARY KEY,
    dex_id BIGINT NOT NULL REFERENCES dex(id),
    ticker_id INT NOT NULL REFERENCES ticker(id),
    interval_hours INT NOT NULL DEFAULT 1,
    next_funding_time TIMESTAMP,
    continuous BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dex_id, ticker_id)
);

CREATE INDEX idx_market_funding_schedule_next ON market_funding_schedule (next_funding_time);

ALTER TABLE last_funding
    ADD COLUMN IF NOT EXISTS next_funding_time TIMESTAMP;

COMMENT ON TABLE funding_settlement IS 'Actually paid funding rate per settlement interval (truth for history)';
COMMENT ON TABLE market_funding_schedule IS 'Per (dex,ticker) funding interval and next settlement time';
