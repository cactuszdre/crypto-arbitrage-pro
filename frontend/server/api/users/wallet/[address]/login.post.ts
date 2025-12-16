/**
 * POST /api/users/wallet/:address/login
 * Updates user last login
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const address = getRouterParam(event, 'address')

    return await $fetch(`${backendUrl}/api/users/wallet/${address}/login`, {
        method: 'POST'
    })
})
