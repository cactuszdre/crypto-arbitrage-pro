/**
 * GET /api/arbitrage/rates
 * Fetches all funding rates for arbitrage calculation
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/arbitrage/rates/all`)
    } catch (error: any) {
        console.error('[API] arbitrage/rates error:', error.message)
        // Return empty array so the app doesn't crash
        return []
    }
})
