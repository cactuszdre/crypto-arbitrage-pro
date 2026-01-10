import { getArbitrageServiceUrl } from '../../utils/backend'

/**
 * GET /api/opportunites/history
 * Fetches historical arbitrage opportunities
 * Maps to backend: /api/arbitrage/opportunities/historical
 * Cached for 1 hour
 */
export default defineCachedEventHandler(async (event) => {
    const fundingUrl = getArbitrageServiceUrl()
    const query = getQuery(event)
    const days = query.days || 7

    try {
        return await $fetch(`${fundingUrl}/api/arbitrage/opportunities/historical?days=${days}`)
    } catch (error: any) {
        console.error('[API] arbitrage/history error:', error.message)
        return { opportunities: [], periodLabel: '', dataPointCount: 0 }
    }
}, {
    maxAge: 60 * 60,
    name: 'arbitrage-history',
    getKey: (event) => `arbitrage-history-${getQuery(event).days}`
})
