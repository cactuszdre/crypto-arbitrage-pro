export default defineEventHandler(async (event) => {
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend('/api/users/admins')
    } catch (error: any) {
        console.error('Error fetching admins:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to fetch admins'
        })
    }
})
