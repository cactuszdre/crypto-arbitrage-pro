/**
 * PATCH /api/dex/:id/toggle
 * Toggles DEX active status
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const id = getRouterParam(event, 'id')
    const query = getQuery(event)

    try {
        return await $fetch(`${backendUrl}/api/dex/${id}/toggle?isActive=${query.isActive}`, {
            method: 'PATCH'
        })
    } catch (error: any) {
        console.error('[API] dex toggle error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to toggle DEX'
        })
    }
})
