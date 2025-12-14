-- V10: Add Extended DEX (X10 Exchange on Starknet)

INSERT INTO dex (name, base_url, funding_interval_hours, active, color, trade_url_pattern, created_at)
SELECT 
    'Extended', 
    'https://api.starknet.extended.exchange', 
    1, -- Hourly funding
    true, 
    '#EC4899', -- Pink color
    'https://app.extended.exchange/perp/{BASE}-USD',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM dex WHERE name = 'Extended');
