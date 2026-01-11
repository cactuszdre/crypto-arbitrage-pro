/**
 * POST /api/users/admins
 * Add a new admin by wallet address
 */
export default defineEventHandler(async (event) => {
    const body = await readBody(event)
    const backendUrl = getBackendUrl()

    if (!body?.walletAddress) {
        throw createError({
            statusCode: 400,
            message: 'walletAddress is required'
        })
    }

    try {
        // Forward the Authorization header if present (for JWT auth)
        const authHeader = getHeader(event, 'Authorization')
        const headers: Record<string, string> = {}
        if (authHeader) {
            headers['Authorization'] = authHeader
        }

        const response = await $fetch(`${backendUrl}/api/users/admins`, {
            method: 'POST',
            body: body,
            headers
        })
        return response
    } catch (error: any) {
        console.error('[API] POST users/admins error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.data?.error || error.message || 'Failed to add admin'
        })
    }
})
