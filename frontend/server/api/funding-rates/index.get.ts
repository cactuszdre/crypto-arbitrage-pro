/**
 * GET /api/funding-rates
 * Fetches all funding rates
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/funding-rates`)
    } catch (error: any) {
        console.error('[API] funding-rates error:', error.message)
        return []
    }
})
