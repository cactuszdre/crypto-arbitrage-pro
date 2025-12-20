/**
 * GET /api/charts/comparison
 * Fetches chart comparison data for a symbol
 * Maps to backend: /api/charts/funding-rate/{symbol}/compare
 * NO CACHE for live data (hours <= 24) to ensure freshness
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const query = getQuery(event)
    const symbol = query.symbol || 'BTC-USD-PERP'
    const hours = Math.round(Number(query.hours) || 24)

    try {
        return await $fetch(`${backendUrl}/api/charts/funding-rate/${symbol}/compare?hours=${hours}`)
    } catch (error: any) {
        console.error('[API] charts/comparison error:', error.message)
        return {}
    }
})

