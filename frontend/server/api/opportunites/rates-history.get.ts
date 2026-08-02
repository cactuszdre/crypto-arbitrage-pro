/**
 * GET /api/opportunites/rates-history
 * Fetches historical rates for arbitrage
 * Maps to backend: /api/arbitrage/rates/historical
 *
 * IMPORTANT: Never return an empty stub on upstream failure — that 200+empty
 * payload gets cached for an hour and blanks the historical period UI.
 */
export default defineCachedEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const query = getQuery(event)
    const days = query.days || 7

    try {
        const data = await $fetch(`${backendUrl}/api/arbitrage/rates/historical?days=${days}`, {
            timeout: 90000
        })
        return data
    } catch (error: unknown) {
        console.error('[API] arbitrage/rates-history error:', error)
        const err = error as { statusCode?: number; statusMessage?: string; message?: string }
        if (err?.statusCode && err.statusCode >= 400) {
            throw error
        }
        throw createError({
            statusCode: 502,
            statusMessage: err?.statusMessage || err?.message || 'Failed to fetch historical rates'
        })
    }
}, {
    maxAge: 60 * 60, // 1 hour — only successful responses are cached
    swr: true,
    staleMaxAge: 60 * 30,
    name: 'arbitrage-rates-history',
    getKey: (event) => `arbitrage-rates-history-${getQuery(event).days}`
})
