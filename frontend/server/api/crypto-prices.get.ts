/**
 * GET /api/crypto-prices
 * Fetches crypto prices
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/crypto-prices`)
    } catch (error: any) {
        console.error('[API] crypto-prices error:', error.message)
        return []
    }
})
