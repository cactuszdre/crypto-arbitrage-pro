-- V2: Add icon_url column to dex_market
ALTER TABLE dex_market ADD COLUMN IF NOT EXISTS icon_url VARCHAR(255);
