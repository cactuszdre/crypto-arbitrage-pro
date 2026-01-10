export default defineEventHandler(async (event) => {
    const body = await readBody(event)
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend('/api/auth/login', {
            method: 'POST',
            body
        })
    } catch (error: any) {
        console.error('Error logging in:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Login failed'
        })
    }
})
