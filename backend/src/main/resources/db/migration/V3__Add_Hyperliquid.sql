-- V3: Add Hyperliquid DEX
INSERT INTO dex (name, base_url, funding_interval_hours) VALUES 
('Hyperliquid', 'https://api.hyperliquid.xyz', 1)
ON CONFLICT (name) DO UPDATE SET base_url = EXCLUDED.base_url, funding_interval_hours = EXCLUDED.funding_interval_hours;
