/**
 * POST /api/dex
 * Creates a new DEX configuration
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const body = await readBody(event)

    try {
        return await $fetch(`${backendUrl}/api/dex`, {
            method: 'POST',
            body
        })
    } catch (error: any) {
        console.error('[API] dex create error:', error.message)
        throw createError({
            statusCode: error.statusCode || 500,
            message: error.message || 'Failed to create DEX'
        })
    }
})
