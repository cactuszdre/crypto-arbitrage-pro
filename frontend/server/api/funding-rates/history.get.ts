import { getFundingServiceUrl } from '../../utils/backend'

/**
 * GET /api/funding-rates/history
 * Fetches funding rate history with query params
 * Cached for 15 minutes
 */
export default defineCachedEventHandler(async (event) => {
    const fundingUrl = getFundingServiceUrl()
    const query = getQuery(event)

    const params = new URLSearchParams()
    if (query.dex) params.append('dex', String(query.dex))
    if (query.symbol) params.append('symbol', String(query.symbol))
    if (query.hours) params.append('hours', String(Math.round(Number(query.hours))))

    try {
        return await $fetch(`${fundingUrl}/api/funding-rates/history?${params.toString()}`)
    } catch (error: any) {
        console.error('[API] funding-rates/history error:', error.message)
        return []
    }
}, {
    maxAge: 15 * 60,
    name: 'funding-rates-history',
    getKey: (event) => {
        const q = getQuery(event)
        return `funding-history-${q.dex}-${q.symbol}-${q.hours}`
    }
})
