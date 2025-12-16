/**
 * PUT /api/dex/:id
 * Updates a DEX configuration
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const id = getRouterParam(event, 'id')
    const body = await readBody(event)

    try {
        return await $fetch(`${backendUrl}/api/dex/${id}`, {
            method: 'PUT',
            body
        })
    } catch (error: any) {
        console.error('[API] dex update error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to update DEX'
        })
    }
})
