export default defineEventHandler(async (event) => {
    const id = getRouterParam(event, 'id')
    const query = getQuery(event)
    const plan = query.plan as string
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/users/${id}/plan?plan=${plan}`, {
            method: 'PATCH'
        })
    } catch (error: any) {
        console.error('Error updating user plan:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to update plan'
        })
    }
})
