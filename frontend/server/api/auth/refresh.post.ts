/**
 * Refresh JWT token
 * POST /api/auth/refresh
 */

interface RefreshResponse {
    token: string
    walletAddress: string
    role: string
}

export default defineEventHandler(async (event): Promise<RefreshResponse> => {
    const authHeader = getHeader(event, 'Authorization')

    if (!authHeader) {
        throw createError({
            statusCode: 401,
            message: 'No authorization header'
        })
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        const response = await $fetch<RefreshResponse>(`${backendUrl}/api/auth/refresh`, {
            method: 'POST',
            headers: {
                Authorization: authHeader
            }
        })
        return response
    } catch (error: any) {
        console.error('[Auth] Token refresh failed:', error)
        throw createError({
            statusCode: error.statusCode || 401,
            message: error.data?.error || 'Token refresh failed'
        })
    }
})
