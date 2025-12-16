/**
 * GET /api/users/wallet/:address
 * Fetches a user by wallet address
 */
export default defineEventHandler(async (event) => {
    const backendUrl = getBackendUrl()
    const address = getRouterParam(event, 'address')

    return await $fetch(`${backendUrl}/api/users/wallet/${address}`)
})
