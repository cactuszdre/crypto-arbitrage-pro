/**
 * PATCH /api/users/:id/plan
 * Updates user plan
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const id = getRouterParam(event, 'id')
    const query = getQuery(event)

    try {
        return await $fetch(`${backendUrl}/api/users/${id}/plan?plan=${query.plan}`, {
            method: 'PATCH'
        })
    } catch (error: any) {
        console.error('[API] user plan update error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to update user plan'
        })
    }
})
