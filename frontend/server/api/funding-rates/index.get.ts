/**
 * GET /api/funding-rates
 * Fetches all funding rates
 * Cached for 30 seconds
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const backendUrl = getBackendUrl()

    try {
        const data = await $fetch<unknown[]>(`${backendUrl}/api/funding-rates`)
        if (!Array.isArray(data)) {
            throw createError({
                statusCode: 502,
                statusMessage: 'Invalid funding-rates payload from backend'
            })
        }
        return data
    } catch (error: unknown) {
        console.error('[API] funding-rates error:', error)
        const err = error as { statusCode?: number; statusMessage?: string; message?: string }
        if (err?.statusCode && err.statusCode >= 400) {
            throw error
        }
        throw createError({
            statusCode: 502,
            statusMessage: err?.statusMessage || err?.message || 'Failed to fetch funding rates from backend'
        })
    }
}, {
    maxAge: 30,
    name: 'funding-rates',
    getKey: () => 'all'
})

