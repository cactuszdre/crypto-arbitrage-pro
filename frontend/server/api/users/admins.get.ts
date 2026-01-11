/**
 * GET /api/users/admins
 * Fetches all admin users
 * Requires ADMIN role (JWT authentication)
 */

interface AdminUser {
    id: number
    walletAddress: string
    role: string
    plan: string
    createdAt: string
    lastLoginAt?: string
}

export default defineEventHandler(async (event): Promise<AdminUser[] | { error: string }> => {
    const backendUrl = getBackendUrl()

    // Forward the Authorization header for JWT authentication
    const authHeader = getHeader(event, 'Authorization')
    const headers: Record<string, string> = {}
    if (authHeader) {
        headers['Authorization'] = authHeader
    }

    try {
        const response = await $fetch<AdminUser[]>(`${backendUrl}/api/users/admins`, {
            headers
        })
        return response
    } catch (error: any) {
        console.error('[API] users/admins error:', error.message)
        // Return 403 instead of empty array if unauthorized
        if (error.statusCode === 403) {
            throw createError({
                statusCode: 403,
                message: 'Admin access required'
            })
        }
        return []
    }
})
