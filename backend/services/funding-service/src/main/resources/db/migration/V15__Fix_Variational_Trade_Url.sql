-- =====================================================
-- Flyway Migration: Fix Variational trade URL pattern
-- Description: Add {BASE} placeholder to trade URL
-- =====================================================

UPDATE dex 
SET trade_url_pattern = 'https://omni.variational.io/perpetual/{BASE}'
WHERE name = 'Variational';
