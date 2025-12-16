/**
 * GET /api/users
 * Fetches all users (admin only)
 */
export default defineEventHandler(async () => {
    const backendUrl = getBackendUrl()
    return await $fetch(`${backendUrl}/api/users`)
})
