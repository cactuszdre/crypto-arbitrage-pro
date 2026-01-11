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

    if (!wallet) {
        throw createError({
            statusCode: 400,
            message: 'Wallet address is required'
        })
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        const response = await $fetch<NonceResponse>(`${backendUrl}/api/auth/nonce/${wallet}`)
        return response
    } catch (error: any) {
        console.error('[Auth] Failed to get nonce:', error)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to get authentication nonce'
        })
    }
})
