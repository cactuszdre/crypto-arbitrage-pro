/**
 * GET /api/funding-rates
 * Fetches all funding rates
 * Cached for 30 seconds
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/funding-rates`)
    } catch (error: unknown) {
        console.error('[API] funding-rates error:', error)
        return []
    }
}, {
    maxAge: 30,
    name: 'funding-rates',
    getKey: () => 'all'
})

