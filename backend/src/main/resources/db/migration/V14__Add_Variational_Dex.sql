-- =====================================================
-- Flyway Migration: Add Variational DEX
-- Description: Add Variational exchange for funding rate tracking
-- API: https://omni.variational.io/api/instruments
-- =====================================================

-- Insert Variational DEX into dex table
INSERT INTO dex (name, base_url, funding_interval_hours, active, color, trade_url_pattern, icon_url, created_at)
SELECT 
    'Variational', 
    'https://omni.variational.io', 
    1, -- Variable funding intervals (1h, 4h, 8h depending on market)
    true, 
    '#8B5CF6', -- Purple color
    'https://omni.variational.io/perpetual',
    '/dex-icons/variational.jpg',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM dex WHERE name = 'Variational');

