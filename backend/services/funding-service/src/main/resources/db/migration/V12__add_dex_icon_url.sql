-- =====================================================
-- Flyway Migration: Add icon_url column to dex table
-- Description: Store DEX logo/icon URLs for frontend display
-- =====================================================

ALTER TABLE dex ADD COLUMN IF NOT EXISTS icon_url VARCHAR(500);

-- Set icon URLs for existing DEXes
UPDATE dex SET icon_url = '/dex-icons/hyperliquid.png' WHERE name = 'Hyperliquid';
UPDATE dex SET icon_url = '/dex-icons/paradex.png' WHERE name = 'Paradex';
UPDATE dex SET icon_url = '/dex-icons/extended.jpg' WHERE name = 'Extended';
UPDATE dex SET icon_url = '/dex-icons/lighter.jpg' WHERE name = 'Lighter';

COMMENT ON COLUMN dex.icon_url IS 'URL to the DEX logo/icon image for frontend display';
