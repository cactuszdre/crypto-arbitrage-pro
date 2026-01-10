import { getArbitrageServiceUrl } from '../../utils/backend'

/**
 * GET /api/opportunites/rates-history
 * Fetches historical rates for arbitrage
 * Maps to backend: /api/arbitrage/rates/historical
 * Cached for 1 hour
 */
export default defineCachedEventHandler(async (event) => {
    const fundingUrl = getArbitrageServiceUrl()
    const query = getQuery(event)
    const days = query.days || 7

    try {
        return await $fetch(`${fundingUrl}/api/arbitrage/rates/historical?days=${days}`)
    } catch (error: any) {
        console.error('[API] arbitrage/rates-history error:', error.message)
        return { symbols: [], periodLabel: '', dataPointCount: 0 }
    }
}, {
    maxAge: 60 * 60, // 1 hour
    name: 'arbitrage-rates-history',
    getKey: (event) => `arbitrage-rates-history-${getQuery(event).days}`
})
