/**
 * PATCH /api/users/:id/role
 * Updates user role
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const id = getRouterParam(event, 'id')
    const query = getQuery(event)

    try {
        return await $fetch(`${backendUrl}/api/users/${id}/role?role=${query.role}`, {
            method: 'PATCH'
        })
    } catch (error: any) {
        console.error('[API] user role update error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to update user role'
        })
    }
})
