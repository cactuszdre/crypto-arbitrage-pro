export default defineEventHandler(async (event) => {
    const walletAddress = getRouterParam(event, 'wallet')
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/users/admins/${walletAddress}`, {
            method: 'DELETE'
        })
    } catch (error: any) {
        console.error('Error removing admin:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to remove admin'
        })
    }
})
