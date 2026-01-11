/**
 * Get current authenticated user
 * GET /api/auth/me
 */

interface MeResponse {
    authenticated: boolean
    walletAddress?: string
    role?: string
    isAdmin?: boolean
    plan?: string
}

export default defineEventHandler(async (event): Promise<MeResponse> => {
    const authHeader = getHeader(event, 'Authorization')

    if (!authHeader) {
        return { authenticated: false }
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        const response = await $fetch<MeResponse>(`${backendUrl}/api/auth/me`, {
            headers: {
                Authorization: authHeader
            }
        })
        return response
    } catch (error: any) {
        console.error('[Auth] Failed to get current user:', error)
        return { authenticated: false }
    }
})
