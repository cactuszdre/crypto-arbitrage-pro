-- V9: Add color and trade_url_pattern columns to dex table
-- These fields enable dynamic frontend configuration without hardcoded values

-- Add new columns
ALTER TABLE dex ADD COLUMN IF NOT EXISTS color VARCHAR(20);
ALTER TABLE dex ADD COLUMN IF NOT EXISTS trade_url_pattern VARCHAR(200);

-- Update existing DEXes with their configuration
UPDATE dex SET 
    color = '#10B981', 
    trade_url_pattern = 'https://app.paradex.trade/trade/{symbol}' 
WHERE name = 'Paradex';

UPDATE dex SET 
    color = '#6366F1', 
    trade_url_pattern = 'https://app.lighter.xyz/trade/{BASE}' 
WHERE name = 'Lighter';

UPDATE dex SET 
    color = '#F59E0B', 
    trade_url_pattern = 'https://app.hyperliquid.xyz/trade/{base}' 
WHERE name = 'Hyperliquid';
