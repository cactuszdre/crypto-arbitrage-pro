export default defineEventHandler(async (event) => {
    const address = getRouterParam(event, 'address')
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/users/wallet/${address}`)
    } catch (error: any) {
        console.error('Error fetching user by wallet:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to fetch user'
        })
    }
})
