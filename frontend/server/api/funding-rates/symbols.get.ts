import { getFundingServiceUrl } from '../../utils/backend'

/**
 * GET /api/funding-rates/symbols
 * Fetches all available symbols from funding-service
 */
export default defineEventHandler(async () => {
    const fundingUrl = getFundingServiceUrl()

    try {
        return await $fetch(`${fundingUrl}/api/funding-rates/symbols`)
    } catch (error: any) {
        console.error('[API] funding-rates/symbols error:', error.message)
        // Return common symbols as fallback
        return ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP', 'ARB-USD-PERP', 'OP-USD-PERP']
    }
})
