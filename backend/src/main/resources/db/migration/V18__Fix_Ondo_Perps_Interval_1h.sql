-- =====================================================
-- Flyway Migration: Fix Ondo Perps funding interval to 1h
-- Description: API rate is hourly (history settles every hour).
--   Previous V17 incorrectly set 3h / 8 periods per day.
-- =====================================================

UPDATE dex
SET funding_interval_hours = 1
WHERE name = 'Ondo Perps'
  AND funding_interval_hours <> 1;
