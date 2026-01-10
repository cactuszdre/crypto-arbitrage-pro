import { getFundingServiceUrl } from '../../utils/backend'

/**
 * GET /api/funding-rates
 * Fetches all funding rates from funding-service microservice
 * Cached for 30 seconds
 */
export default defineCachedEventHandler(async (): Promise<unknown[]> => {
    const fundingUrl = getFundingServiceUrl()

    try {
        // Using backwards-compatible path /api/funding-rates
        return await $fetch(`${fundingUrl}/api/funding-rates`)
    } catch (error: unknown) {
        console.error('[API] funding-rates error:', error)
        return []
    }
}, {
    maxAge: 30,
    name: 'funding-rates',
    getKey: () => 'all'
})
