export default defineEventHandler(async (event) => {
    const address = getRouterParam(event, 'address')
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/users/wallet/${address}/login`, {
            method: 'POST'
        })
    } catch (error: any) {
        console.error('Error updating last login:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to update last login'
        })
    }
})
