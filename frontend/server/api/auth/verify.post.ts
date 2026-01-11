/**
 * Verify wallet signature and get JWT token
 * POST /api/auth/verify
 */

interface VerifyRequest {
    walletAddress: string
    signature: string
    nonce: string
}

interface VerifyResponse {
    token: string
    walletAddress: string
    role: string
    isAdmin: boolean
}

export default defineEventHandler(async (event): Promise<VerifyResponse> => {
    const body = await readBody<VerifyRequest>(event)

    if (!body.walletAddress || !body.signature || !body.nonce) {
        throw createError({
            statusCode: 400,
            message: 'Missing required fields: walletAddress, signature, nonce'
        })
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        const response = await $fetch<VerifyResponse>(`${backendUrl}/api/auth/verify`, {
            method: 'POST',
            body: body
        })
        return response
    } catch (error: any) {
        console.error('[Auth] Signature verification failed:', error)
        throw createError({
            statusCode: error.statusCode || 401,
            message: error.data?.error || 'Invalid signature'
        })
    }
})
