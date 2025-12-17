/**
 * GET /api/opportunites/rates
 * Fetches all funding rates for arbitrage calculation
 * Cached for 30 seconds to reduce latency
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/arbitrage/rates/all`)
    } catch (error: unknown) {
        console.error('[API] arbitrage/rates error:', error)
        // Return empty array so the app doesn't crash
        return []
    }
}, {
    maxAge: 30, // Cache for 30 seconds
    name: 'arbitrage-rates',
    getKey: () => 'all'
})

