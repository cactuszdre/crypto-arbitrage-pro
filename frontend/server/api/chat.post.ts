/**
 * POST /api/chat
 * Sends a message to the AI chat
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const body = await readBody(event)

    return await $fetch(`${backendUrl}/api/chat`, {
        method: 'POST',
        body
    })
})
