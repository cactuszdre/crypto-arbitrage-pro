/**
 * GET /api/opportunites/history
 * Fetches historical arbitrage opportunities
 * Maps to backend: /api/arbitrage/opportunities/historical
 *
 * Never cache empty stubs from upstream failures.
 */
export default defineCachedEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const query = getQuery(event)
    const days = query.days || 7

    try {
        return await $fetch(`${backendUrl}/api/arbitrage/opportunities/historical?days=${days}`, {
            timeout: 90000
        })
    } catch (error: unknown) {
        console.error('[API] arbitrage/history error:', error)
        const err = error as { statusCode?: number; statusMessage?: string; message?: string }
        if (err?.statusCode && err.statusCode >= 400) {
            throw error
        }
        throw createError({
            statusCode: 502,
            statusMessage: err?.statusMessage || err?.message || 'Failed to fetch historical opportunities'
        })
    }
}, {
    maxAge: 60 * 60,
    swr: true,
    staleMaxAge: 60 * 30,
    name: 'arbitrage-history',
    getKey: (event) => `arbitrage-history-${getQuery(event).days}`
})
