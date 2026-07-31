-- =====================================================
-- Flyway Migration: Add Ondo Perps
-- Description: Add Ondo Perps exchange for funding rate tracking
-- API: https://api.ondoperps.xyz
-- Markets: Crypto, US Stocks, KR Stocks, ETFs, Indices, Commodities
-- =====================================================

INSERT INTO dex (name, base_url, funding_interval_hours, active, color, trade_url_pattern, icon_url, created_at)
SELECT
    'Ondo Perps',
    'https://api.ondoperps.xyz',
    3,  -- 8 funding intervals per day = every 3 hours
    true,
    '#0052FF',  -- Ondo brand blue
    'https://app.ondoperps.xyz/trade/perps/{BASE}-USD.P',
    '/dex-icons/ondoperps.png',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM dex WHERE name = 'Ondo Perps');
