/**
 * GET /api/users/admins
 * Fetches all admin users
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()

    try {
        return await $fetch(`${backendUrl}/api/users/admins`)
    } catch (error: any) {
        console.error('[API] users/admins error:', error.message)
        return []
    }
})
