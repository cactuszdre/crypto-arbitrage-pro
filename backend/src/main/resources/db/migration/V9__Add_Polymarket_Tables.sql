-- Polymarket arbitrage tables for tracking positions and trades

-- Table for tracking user positions on Polymarket markets
CREATE TABLE polymarket_position (
    id BIGSERIAL PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    market_id VARCHAR(100) NOT NULL,
    market_question VARCHAR(500),
    market_end_time TIMESTAMP,
    yes_shares DECIMAL(20, 8) DEFAULT 0,
    no_shares DECIMAL(20, 8) DEFAULT 0,
    yes_cost DECIMAL(20, 8) DEFAULT 0,
    no_cost DECIMAL(20, 8) DEFAULT 0,
    avg_combined_cost DECIMAL(10, 6),
    imbalance_ratio DECIMAL(10, 6),
    status VARCHAR(20) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolution VARCHAR(10),
    pnl DECIMAL(20, 8),
    CONSTRAINT uk_wallet_market UNIQUE (wallet_address, market_id)
);

-- Table for logging individual trades (for audit/history)
CREATE TABLE polymarket_trade (
    id BIGSERIAL PRIMARY KEY,
    position_id BIGINT REFERENCES polymarket_position(id),
    wallet_address VARCHAR(42) NOT NULL,
    market_id VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL, -- 'YES' or 'NO'
    order_type VARCHAR(10) NOT NULL, -- 'BUY' or 'SELL'
    shares DECIMAL(20, 8) NOT NULL,
    price DECIMAL(10, 6) NOT NULL,
    total_cost DECIMAL(20, 8) NOT NULL,
    tx_hash VARCHAR(66),
    executed_at TIMESTAMP DEFAULT NOW()
);

-- Table for caching market data (to reduce API calls)
CREATE TABLE polymarket_market_cache (
    id BIGSERIAL PRIMARY KEY,
    market_id VARCHAR(100) NOT NULL UNIQUE,
    question VARCHAR(500),
    condition_id VARCHAR(100),
    token_id_yes VARCHAR(100),
    token_id_no VARCHAR(100),
    end_date TIMESTAMP,
    category VARCHAR(50),
    yes_price DECIMAL(10, 6),
    no_price DECIMAL(10, 6),
    combined_price DECIMAL(10, 6),
    volume_24h DECIMAL(20, 8),
    liquidity DECIMAL(20, 8),
    last_updated TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_position_wallet ON polymarket_position(wallet_address);
CREATE INDEX idx_position_status ON polymarket_position(status);
CREATE INDEX idx_trade_wallet ON polymarket_trade(wallet_address);
CREATE INDEX idx_trade_executed ON polymarket_trade(executed_at);
CREATE INDEX idx_market_cache_updated ON polymarket_market_cache(last_updated);
CREATE INDEX idx_market_cache_category ON polymarket_market_cache(category);
