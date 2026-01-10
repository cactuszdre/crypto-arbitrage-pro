export default defineEventHandler(async (event) => {
    const address = getRouterParam(event, 'address')
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/auth/nonce/${address}`)
    } catch (error) {
        console.error('Error fetching nonce:', error)
        throw createError({
            statusCode: 500,
            statusMessage: 'Failed to fetch nonce from auth-service'
        })
    }
})
