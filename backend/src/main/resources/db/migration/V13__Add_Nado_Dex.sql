-- =====================================================
-- Flyway Migration: Add Nado DEX
-- Description: Add Nado exchange (on Ink/L2) for funding rate tracking
-- Nado API: https://docs.nado.xyz/developer-resources/api
-- =====================================================

-- Insert Nado DEX into dex table
INSERT INTO dex (name, base_url, funding_interval_hours, active, color, trade_url_pattern, icon_url, created_at)
SELECT 
    'Nado', 
    'https://gateway.prod.nado.xyz', 
    1, -- Hourly funding (settled every hour)
    true, 
    '#8B5CF6', -- Purple/Violet color for Nado
    'https://app.nado.xyz/perpetuals',
    '/dex-icons/nado.png',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM dex WHERE name = 'Nado');
