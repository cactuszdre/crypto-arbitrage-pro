CREATE TABLE IF NOT EXISTS dex (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    base_url VARCHAR(255),
    api_key VARCHAR(1000),
    funding_interval_hours INT NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dex_market (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dex_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    original_symbol VARCHAR(50),
    active BOOLEAN DEFAULT true,
    updated_at DATETIME,
    FOREIGN KEY (dex_id) REFERENCES dex(id),
    UNIQUE KEY uk_dex_symbol (dex_id, symbol)
);

CREATE TABLE IF NOT EXISTS funding_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dex_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    rate DECIMAL(20, 12) NOT NULL,
    rate_8h DECIMAL(20, 12),
    rate_annual DECIMAL(20, 4),
    funding_premium DECIMAL(20, 8),
    funding_index DECIMAL(30, 12),
    interval_hours INT NOT NULL,
    timestamp DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dex_id) REFERENCES dex(id),
    INDEX idx_dex_symbol_time (dex_id, symbol, timestamp),
    INDEX idx_timestamp (timestamp)
);

CREATE TABLE IF NOT EXISTS arbitrage_opportunity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dex1_id BIGINT NOT NULL,
    dex2_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    dex1_rate DECIMAL(20, 12) NOT NULL,
    dex2_rate DECIMAL(20, 12) NOT NULL,
    spread DECIMAL(20, 12) NOT NULL,
    projected_daily_return DECIMAL(20, 8),
    recommended_long_dex VARCHAR(50),
    recommended_short_dex VARCHAR(50),
    detected_at DATETIME NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    FOREIGN KEY (dex1_id) REFERENCES dex(id),
    FOREIGN KEY (dex2_id) REFERENCES dex(id),
    INDEX idx_symbol_time (symbol, detected_at)
);

CREATE TABLE IF NOT EXISTS position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dex_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    size DECIMAL(18, 8) NOT NULL,
    entry_price DECIMAL(18, 8),
    opened_at DATETIME NOT NULL,
    closed_at DATETIME,
    pnl DECIMAL(18, 8),
    status VARCHAR(20) DEFAULT 'OPEN',
    FOREIGN KEY (dex_id) REFERENCES dex(id)
);

-- Initial Data Seeding
INSERT INTO dex (name, base_url, funding_interval_hours) VALUES 
('Paradex', 'https://api.prod.paradex.trade/v1', 8)
ON DUPLICATE KEY UPDATE base_url=VALUES(base_url);

INSERT INTO dex (name, base_url, funding_interval_hours) VALUES 
('Lighter', 'https://mainnet.zklighter.elliot.ai/api/v1', 1)
ON DUPLICATE KEY UPDATE base_url=VALUES(base_url);
