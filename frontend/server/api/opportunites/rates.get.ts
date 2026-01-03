/**
 * GET /api/opportunites/rates
 * Fetches all funding rates for arbitrage calculation
 * Cached for 8 seconds to align with 10s frontend refresh
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
    maxAge: 8, // Cache for 8 seconds (frontend refreshes every 10s)
    name: 'arbitrage-rates',
    getKey: () => 'all'
})

