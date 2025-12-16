/**
 * GET /api/charts/comparison-hourly
 * Fetches hourly chart comparison data
 * Maps to backend: /api/charts/funding-rate/{symbol}/compare-hourly
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const query = getQuery(event)
    const symbol = query.symbol || 'BTC-USD-PERP'
    const hours = query.hours || 168

    try {
        return await $fetch(`${backendUrl}/api/charts/funding-rate/${symbol}/compare-hourly?hours=${hours}`)
    } catch (error: any) {
        console.error('[API] charts/comparison-hourly error:', error.message)
        return {}
    }
})
