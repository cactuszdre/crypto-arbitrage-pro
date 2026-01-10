import { getArbitrageServiceUrl } from '../../utils/backend'

/**
 * GET /api/opportunites/rates
 * Fetches all funding rates for arbitrage calculation from arbitrage-service
 * Cached for 8 seconds to align with 10s frontend refresh
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const fundingUrl = getArbitrageServiceUrl()

    try {
        return await $fetch(`${fundingUrl}/api/arbitrage/rates/all`)
    } catch (error: unknown) {
        console.error('[API] arbitrage/rates error:', error)
        return []
    }
}, {
    maxAge: 8,
    name: 'arbitrage-rates',
    getKey: () => 'all'
})
