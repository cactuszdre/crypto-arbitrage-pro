export default defineEventHandler(async (event) => {
    const body = await readBody(event)
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend('/api/users/admins', {
            method: 'POST',
            body
        })
    } catch (error: any) {
        console.error('Error adding admin:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to add admin'
        })
    }
})
