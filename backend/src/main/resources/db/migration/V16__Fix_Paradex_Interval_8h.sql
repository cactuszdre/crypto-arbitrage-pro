-- =====================================================
-- Flyway Migration: Fix Paradex funding interval to 8h
-- Description: Paradex changed all their funding periods to 8h.
--              This updates existing data with old intervals (1h, 2h, 4h)
--              to the new 8h interval and recalculates rates.
-- =====================================================

-- Get Paradex DEX ID for use in updates
DO $$
DECLARE
    paradex_dex_id BIGINT;
BEGIN
    SELECT id INTO paradex_dex_id FROM dex WHERE name = 'Paradex';
    
    IF paradex_dex_id IS NOT NULL THEN
        -- Update last_funding table for Paradex entries with wrong interval
        -- Recalculate rate_annual: rate (hourly) -> annualized
        -- rate_annual = rate * 8 (hours per period) * 3 (periods per day) * 365 * 100
        -- But rate is stored as hourly, so: rate_annual = rate * 24 * 365 * 100
        UPDATE last_funding
        SET 
            interval_hours = 8,
            -- rate is hourly, rate_8h = rate * 8 * 100 (already correct formula)
            rate_8h = rate * 8 * 100,
            -- rate_annual = rate * 24 * 365 * 100 (hourly rate to annual %)
            rate_annual = ROUND(rate * 24 * 365 * 100, 2),
            updated_at = NOW()
        WHERE dex_id = paradex_dex_id 
        AND interval_hours != 8;

        -- Update funding_rate history table for Paradex entries with wrong interval
        UPDATE funding_rate
        SET 
            interval_hours = 8,
            rate_8h = rate * 8 * 100,
            rate_annual = ROUND(rate * 24 * 365 * 100, 2)
        WHERE dex_id = paradex_dex_id 
        AND interval_hours != 8;

        RAISE NOTICE 'Fixed Paradex funding intervals to 8h';
    ELSE
        RAISE NOTICE 'Paradex DEX not found, skipping migration';
    END IF;
END $$;
