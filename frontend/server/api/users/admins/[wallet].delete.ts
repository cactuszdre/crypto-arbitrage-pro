/**
 * DELETE /api/users/admins/[wallet]
 * Remove admin role from a user
 */
export default defineEventHandler(async (event) => {
    const wallet = getRouterParam(event, 'wallet')
    const backendUrl = getBackendUrl()

    if (!wallet) {
        throw createError({
            statusCode: 400,
            message: 'wallet address is required'
        })
    }

    try {
        // Forward the Authorization header if present (for JWT auth)
        const authHeader = getHeader(event, 'Authorization')
        const headers: Record<string, string> = {}
        if (authHeader) {
            headers['Authorization'] = authHeader
        }

        const response = await $fetch(`${backendUrl}/api/users/admins/${wallet}`, {
            method: 'DELETE',
            headers
        })
        return response
    } catch (error: any) {
        console.error('[API] DELETE users/admins error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.data?.error || error.message || 'Failed to remove admin'
        })
    }
})
