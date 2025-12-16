/**
 * GET /api/funding-rates/symbols
 * Fetches all available symbols
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/funding-rates/symbols`)
    } catch (error: any) {
        console.error('[API] funding-rates/symbols error:', error.message)
        // Return common symbols as fallback
        return ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP', 'ARB-USD-PERP', 'OP-USD-PERP']
    }
})
