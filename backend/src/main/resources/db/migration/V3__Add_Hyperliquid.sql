-- Add Hyperliquid DEX
INSERT INTO dex (name, base_url, funding_interval_hours) VALUES 
('Hyperliquid', 'https://api.hyperliquid.xyz', 1)
ON DUPLICATE KEY UPDATE base_url=VALUES(base_url), funding_interval_hours=VALUES(funding_interval_hours);
