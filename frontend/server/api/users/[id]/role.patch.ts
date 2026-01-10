export default defineEventHandler(async (event) => {
    const id = getRouterParam(event, 'id')
    const query = getQuery(event)
    const role = query.role as string
    const fetchBackend = useBackendFetch()

    try {
        return await fetchBackend(`/api/users/${id}/role?role=${role}`, {
            method: 'PATCH'
        })
    } catch (error: any) {
        console.error('Error updating user role:', error)
        throw createError({
            statusCode: error.response?.status || 500,
            statusMessage: 'Failed to update role'
        })
    }
})
