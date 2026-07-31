/**
 * GET /api/dex
 * Fetches all DEX configurations
 */

const FALLBACK_DEXES = [
    { name: 'Hyperliquid', color: '#F59E0B', active: true, iconUrl: '/dex-icons/hyperliquid.png', tradeUrlPattern: 'https://app.hyperliquid.xyz/trade/{base}' },
    { name: 'Paradex', color: '#10B981', active: true, iconUrl: '/dex-icons/paradex.png', tradeUrlPattern: 'https://app.paradex.trade/trade/{symbol}' },
    { name: 'Lighter', color: '#6366F1', active: true, iconUrl: '/dex-icons/lighter.jpg', tradeUrlPattern: 'https://app.lighter.xyz/trade/{BASE}' },
    { name: 'Extended', color: '#EC4899', active: true, iconUrl: '/dex-icons/extended.jpg', tradeUrlPattern: 'https://app.extended.exchange/perp/{BASE}-USD' },
    { name: 'Nado', color: '#8B5CF6', active: true, iconUrl: '/dex-icons/nado.png', tradeUrlPattern: 'https://app.nado.xyz/perpetuals' },
    { name: 'Variational', color: '#8B5CF6', active: true, iconUrl: '/dex-icons/variational.jpg', tradeUrlPattern: 'https://omni.variational.io/perpetual/{BASE}' },
    { name: 'Ondo Perps', color: '#0052FF', active: true, iconUrl: '/dex-icons/ondoperps.png', tradeUrlPattern: 'https://app.ondoperps.xyz/trade/perps/{BASE}-USD.P' },
]

export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/dex`, { timeout: 10000 })
    } catch (error: any) {
        console.error('[API] dex error:', error.message)
        // Full fallback with icons so a backend blip doesn't blank logos / drop DEXes
        return FALLBACK_DEXES
    }
})
