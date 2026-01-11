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

    console.log('[Auth Verify] Received request:', {
        walletAddress: body.walletAddress,
        hasSignature: !!body.signature,
        signatureLength: body.signature?.length,
        nonce: body.nonce?.substring(0, 20) + '...'
    })

    if (!body.walletAddress || !body.signature || !body.nonce) {
        console.error('[Auth Verify] Missing fields:', {
            hasWallet: !!body.walletAddress,
            hasSignature: !!body.signature,
            hasNonce: !!body.nonce
        })
        throw createError({
            statusCode: 400,
            message: 'Missing required fields: walletAddress, signature, nonce'
        })
    }

    const backendUrl = useRuntimeConfig().backendUrl || 'http://localhost:8080'

    try {
        console.log('[Auth Verify] Forwarding to backend:', backendUrl)
        const response = await $fetch<VerifyResponse>(`${backendUrl}/api/auth/verify`, {
            method: 'POST',
            body: body
        })
        console.log('[Auth Verify] Backend success for:', body.walletAddress)
        return response
    } catch (error: any) {
        console.error('[Auth Verify] Backend error:', {
            status: error.statusCode,
            message: error.data?.error || error.message,
            wallet: body.walletAddress
        })
        throw createError({
            statusCode: error.statusCode || 401,
            message: error.data?.error || 'Invalid signature'
        })
    }
})
