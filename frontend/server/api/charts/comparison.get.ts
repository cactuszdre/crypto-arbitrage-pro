/**
 * GET /api/charts/comparison
 * Fetches chart comparison data for a symbol
 * Maps to backend: /api/charts/funding-rate/{symbol}/compare
 * Cached for 60 seconds
 */
export default defineCachedEventHandler(async (event) => {
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
}, {
    maxAge: 60,
    name: 'charts-comparison',
    getKey: (event) => {
        const query = getQuery(event)
        return `${query.symbol}-${query.hours}`
    }
})

