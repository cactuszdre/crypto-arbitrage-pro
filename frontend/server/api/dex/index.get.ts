/**
 * GET /api/dex
 * Fetches all DEX configurations
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/dex`)
    } catch (error: any) {
        console.error('[API] dex error:', error.message)
        // Return fallback DEX data so the app works without backend
        return [
            { name: 'Hyperliquid', color: '#F59E0B', isActive: true },
            { name: 'Paradex', color: '#10B981', isActive: true },
            { name: 'Lighter', color: '#6366F1', isActive: true }
        ]
    }
})
