import { getArbitrageServiceUrl } from '../../utils/backend'

/**
 * GET /api/charts/comparison-daily
 * Fetches daily chart comparison data
 * Maps to backend: /api/charts/funding-rate/{symbol}/compare-daily
 */
export default defineEventHandler(async (event) => {
    const fundingUrl = getArbitrageServiceUrl()
    const query = getQuery(event)
    const symbol = query.symbol || 'BTC-USD-PERP'
    const days = query.days || 30

    try {
        return await $fetch(`${fundingUrl}/api/charts/funding-rate/${symbol}/compare-daily?days=${days}`)
    } catch (error: any) {
        console.error('[API] charts/comparison-daily error:', error.message)
        return {}
    }
})
