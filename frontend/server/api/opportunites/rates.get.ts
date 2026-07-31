/**
 * GET /api/opportunites/rates
 * Fetches all funding rates for arbitrage calculation
 * Cached for 8 seconds to align with 10s frontend refresh
 *
 * IMPORTANT: Never return [] on upstream failure — that 200+empty response
 * gets cached and wipes the opportunities UI. Propagate errors so the
 * client can keep its last good data.
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const backendUrl = getBackendUrl()

    try {
        const data = await $fetch<unknown[]>(`${backendUrl}/api/arbitrage/rates/all`, {
            timeout: 20000
        })
        if (!Array.isArray(data)) {
            throw createError({
                statusCode: 502,
                statusMessage: 'Invalid rates payload from backend'
            })
        }
        return data
    } catch (error: unknown) {
        console.error('[API] arbitrage/rates error:', error)
        const err = error as { statusCode?: number; statusMessage?: string; message?: string }
        // Re-throw H3/createError as-is; wrap upstream fetch failures
        if (err?.statusCode && err.statusCode >= 400) {
            throw error
        }
        throw createError({
            statusCode: 502,
            statusMessage: err?.statusMessage || err?.message || 'Failed to fetch rates from backend'
        })
    }
}, {
    maxAge: 8, // Cache for 8 seconds (frontend refreshes every 10s)
    swr: true,
    staleMaxAge: 60, // Serve last good cached payload briefly while backend recovers
    name: 'arbitrage-rates',
    getKey: () => 'all'
})
