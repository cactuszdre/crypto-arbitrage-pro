/**
 * Request authentication nonce
 * GET /api/auth/nonce/[wallet]
 */

interface NonceResponse {
    nonce: string
    message: string
}

export default defineEventHandler(async (event): Promise<NonceResponse> => {
    const wallet = getRouterParam(event, 'wallet')
    const timestamp = Date.now()

    console.log(`[Auth Nonce] Request received at ${timestamp} for wallet: ${wallet}`)

    if (!wallet) {
        throw createError({
            statusCode: 400,
            message: 'Wallet address is required'
        })
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        console.log(`[Auth Nonce] Forwarding to backend for wallet: ${wallet}`)
        const response = await $fetch<NonceResponse>(`${backendUrl}/api/auth/nonce/${wallet}`)
        console.log(`[Auth Nonce] Got nonce for ${wallet}: ${response.nonce.substring(0, 10)}...`)
        return response
    } catch (error: any) {
        console.error('[Auth Nonce] Failed to get nonce:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to get authentication nonce'
        })
    }
})
