/**
 * GET /api/funding-rates/history
 * Fetches funding rate history with query params
 * Cached for 15 minutes — never cache empty stubs from upstream failures.
 */
export default defineCachedEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const query = getQuery(event)

    const params = new URLSearchParams()
    if (query.dex) params.append('dex', String(query.dex))
    if (query.symbol) params.append('symbol', String(query.symbol))
    if (query.hours) params.append('hours', String(Math.round(Number(query.hours))))

    try {
        return await $fetch(`${backendUrl}/api/funding-rates/history?${params.toString()}`, {
            timeout: 60000
        })
    } catch (error: unknown) {
        console.error('[API] funding-rates/history error:', error)
        const err = error as { statusCode?: number; statusMessage?: string; message?: string }
        if (err?.statusCode && err.statusCode >= 400) {
            throw error
        }
        throw createError({
            statusCode: 502,
            statusMessage: err?.statusMessage || err?.message || 'Failed to fetch funding rate history'
        })
    }
}, {
    maxAge: 15 * 60,
    swr: true,
    name: 'funding-rates-history',
    getKey: (event) => {
        const q = getQuery(event)
        return `funding-history-${q.dex}-${q.symbol}-${q.hours}`
    }
})
