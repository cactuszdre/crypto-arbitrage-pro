/**
 * Secure Authentication Composable
 * Implements wallet-based authentication with cryptographic signature verification
 * 
 * Flow:
 * 1. User connects wallet via WalletConnect/MetaMask
 * 2. Frontend requests a nonce from backend
 * 3. User signs the nonce with their wallet
 * 4. Backend verifies signature and returns JWT
 * 5. JWT is stored and sent with all authenticated requests
 */

// Global state for authentication process
const isAuthenticating = ref(false)
const authError = ref<string | null>(null)

export const useSecureAuth = () => {
    const { setUser, setToken, getToken, logout: clearAuth, user, token, getStoredAddress, isAddressMatch, clearIfAddressMismatch } = useAuth()
    const { accountData, appKit } = useAppKit()

    /**
     * Check if we have a valid token
     */
    const hasValidToken = computed(() => !!getToken())

    /**
     * Request a nonce from the backend
     */
    const requestNonce = async (walletAddress: string): Promise<{ nonce: string; message: string }> => {
        const response = await $fetch<{ nonce: string; message: string }>(
            `/api/auth/nonce/${walletAddress}?_t=${Date.now()}`
        )
        return response
    }

    /**
     * Sign a message using the connected wallet
     * Uses window.ethereum directly (works with MetaMask, WalletConnect, etc.)
     */
    const signMessage = async (message: string, walletAddress: string): Promise<string> => {
        // Priority 1: Get provider directly from AppKit (most reliable for current connection)
        // @ts-ignore - getProvider exists on AppKit instance
        let provider = accountData.value?.connector?.provider || appKit.value?.getProvider?.()

        // Priority 2: Fallback to window.ethereum
        if (!provider) {
            provider = (window as any).ethereum
        }

        if (!provider) {
            throw new Error('No Ethereum provider found. Please install a wallet.')
        }

        try {
            // Use personal_sign (EIP-191)
            // Note: params order can vary, but [message, address] is standard for personal_sign
            const signature = await provider.request({
                method: 'personal_sign',
                params: [message, walletAddress]
            })
            return signature as string
        } catch (error: any) {
            // User rejected the signature request
            if (error.code === 4001) {
                throw new Error('Signature rejected by user')
            }
            throw error
        }
    }

    /**
     * Verify signature with backend and get JWT
     */
    const verifyAndGetToken = async (
        walletAddress: string,
        signature: string,
        nonce: string
    ): Promise<{ token: string; role: string; isAdmin: boolean; plan?: string }> => {
        console.log('[SecureAuth] Sending verify request:', {
            walletAddress,
            nonce: nonce.substring(0, 20) + '...',
            signatureLength: signature.length
        })
        const response = await $fetch<{
            token: string
            walletAddress: string
            role: string
            isAdmin: boolean
            plan?: string
        }>('/api/auth/verify', {
            method: 'POST',
            body: { walletAddress, signature, nonce }
        })
        return response
    }

    /**
     * Full authentication flow
     * Called after wallet is connected
     */
    const authenticate = async (inputAddress: string): Promise<boolean> => {
        if (isAuthenticating.value) return false
        isAuthenticating.value = true
        authError.value = null

        try {
            // =========================================================
            // PHASE 0: Early Address Validation & Mismatch Detection
            // =========================================================
            
            // Clean the address - remove any chain prefix (eip155:chainId:address format)
            let cleanedAddress = inputAddress
            if (cleanedAddress.includes(':')) {
                cleanedAddress = cleanedAddress.split(':').pop() || cleanedAddress
            }
            
            // Validate address format
            if (!cleanedAddress || cleanedAddress.length !== 42 || !cleanedAddress.startsWith('0x')) {
                throw new Error(`Invalid wallet address format: ${cleanedAddress}`)
            }
            
            console.log('[SecureAuth] Authenticating address:', cleanedAddress)
            
            const normalizedInput = cleanedAddress.toLowerCase()
            const existingStoredAddress = getStoredAddress()
            const existingToken = getToken()

            // If we have existing auth for a DIFFERENT address, clear it first
            if (existingToken && existingStoredAddress && existingStoredAddress !== normalizedInput) {
                console.log('[SecureAuth] Wallet switch detected!')
                console.log('[SecureAuth] Previously authenticated:', existingStoredAddress)
                console.log('[SecureAuth] Now connecting:', normalizedInput)
                clearIfAddressMismatch(normalizedInput)
            }

            // If we already have valid auth for THIS address, just verify token validity
            if (existingToken && isAddressMatch(normalizedInput)) {
                console.log('[SecureAuth] Found existing auth for current address, validating...')
                try {
                    const meResponse = await $fetch<{
                        authenticated: boolean
                        walletAddress: string
                    }>('/api/auth/me', {
                        headers: { Authorization: `Bearer ${existingToken}` }
                    })
                    
                    if (meResponse.authenticated && meResponse.walletAddress.toLowerCase() === normalizedInput) {
                        console.log('[SecureAuth] Existing token is valid, skipping re-authentication')
                        isAuthenticating.value = false
                        return true
                    }
                } catch (e) {
                    console.log('[SecureAuth] Existing token invalid, proceeding with fresh auth')
                    clearAuth()
                }
            }

            // Priority 1: Get provider directly from AppKit
            // @ts-ignore
            let provider = accountData.value?.connector?.provider || appKit.value?.getProvider?.() // useAppKit hook
            if (!provider) {
                provider = (window as any).ethereum
            }
            if (!provider) throw new Error('No provider found')

            // ---------------------------------------------------------
            // PHASE 1: Resolve Active Address (Source of Truth)
            // ---------------------------------------------------------
            let activeAddress: string | undefined
            let accounts: string[] = []

            // Poll for up to 3 seconds for provider readiness
            for (let i = 0; i < 6; i++) {
                try {
                    accounts = await provider.request({ method: 'eth_accounts' })

                    if (accounts && accounts.length > 0) {
                        break // Provider is ready
                    }

                    // If still empty after 3 attempts, try forcing requestAccounts once
                    if (i === 3 && (!accounts || accounts.length === 0)) {
                        accounts = await provider.request({ method: 'eth_requestAccounts' })
                        if (accounts && accounts.length > 0) break
                    }

                } catch (err) {
                    // ignore
                }
                // Wait 150ms before next attempt
                if (i < 5) await new Promise(r => setTimeout(r, 150))
            }

            try {
                // Find matching account (case-insensitive)
                const match = accounts?.find((acc: string) => acc.toLowerCase() === cleanedAddress.toLowerCase())

                if (match) {
                    activeAddress = match
                } else if (accounts?.length > 0) {
                    // Fallback if no specific match
                    activeAddress = cleanedAddress
                }
            } catch (err) {
                // ignore
            }

            if (!activeAddress) {
                activeAddress = cleanedAddress
            }

            // use the resolved address for the entire flow
            const addressToAuth = activeAddress

            // ---------------------------------------------------------
            // PHASE 2: Authenticate (Nonce -> Sign -> Verify)
            // Includes Auto-Retry Logic (Max 2 Attempts)
            // ---------------------------------------------------------
            let lastError: any = null

            for (let attempt = 1; attempt <= 2; attempt++) {
                try {
                    // Step 1: Request nonce (Fresh request)
                    console.log(`[SecureAuth] Attempt ${attempt}: Requesting nonce for ${addressToAuth}`)
                    const { nonce, message } = await requestNonce(addressToAuth)
                    console.log(`[SecureAuth] Got nonce: ${nonce.substring(0, 10)}...`)
                    console.log(`[SecureAuth] Full message to sign:`, JSON.stringify(message))
                    console.log(`[SecureAuth] Message length: ${message.length}`)

                    // Step 2: Sign message
                    console.log('[SecureAuth] Requesting signature...')
                    console.log(`[SecureAuth] Signing with address: ${addressToAuth}`)
                    const signature = await provider.request({
                        method: 'personal_sign',
                        params: [message, addressToAuth]
                    })
                    console.log('[SecureAuth] Signature obtained:', signature.substring(0, 20) + '...')

                    // Step 3: Verify with backend
                    console.log('[SecureAuth] Verifying with backend...')
                    console.log(`[SecureAuth] Verify payload: wallet=${addressToAuth}, nonce=${nonce.substring(0, 10)}...`)
                    const { token, role, plan } = await verifyAndGetToken(addressToAuth, signature as string, nonce)

                    // Step 4: Success - Store token and user
                    setUser({
                        walletAddress: addressToAuth,
                        role,
                        plan: plan || 'FREE'
                    }, token)

                    return true // Success exit

                } catch (error: any) {
                    // warn specific retry
                    if (attempt < 2) {
                        console.warn(`[SecureAuth] Attempt ${attempt} failed, retrying...`)
                        lastError = error
                        // Wait 300ms before retrying
                        await new Promise(r => setTimeout(r, 300))
                    } else {
                        throw error // Rethrow on last attempt
                    }
                }
            }

            // Should not reach here
            return false

        } catch (error: any) {
            console.error('Authentication final failure:', error)
            authError.value = error.message || 'Authentication failed'

            // Clear any existing auth to be safe
            logout()
            return false
        } finally {
            isAuthenticating.value = false
        }
    }

    /**
     * Refresh user details from backend
     */
    const refreshUserDetails = async () => {
        const currentToken = getToken()
        if (!currentToken) return

        try {
            const response = await $fetch<{
                authenticated: boolean
                walletAddress: string
                role: string
                isAdmin: boolean
                plan: string
            }>('/api/auth/me', {
                headers: { Authorization: `Bearer ${currentToken}` }
            })

            if (response.authenticated) {
                setUser({
                    walletAddress: response.walletAddress,
                    role: response.role,
                    plan: response.plan
                })
            } else {
                // Token invalid, logout
                logout()
            }
        } catch (error) {
            console.error('[SecureAuth] Failed to refresh user details:', error)
        }
    }

    /**
     * Refresh the JWT token before it expires
     */
    const refreshToken = async (): Promise<boolean> => {
        const currentToken = getToken()
        if (!currentToken) return false

        try {
            const response = await $fetch<{ token: string }>('/api/auth/refresh', {
                method: 'POST',
                headers: { Authorization: `Bearer ${currentToken}` }
            })

            setToken(response.token)
            return true
        } catch (error) {
            console.error('[SecureAuth] Token refresh failed:', error)
            logout()
            return false
        }
    }

    /**
     * Logout - clear token and user state
     */
    const logout = () => {
        authError.value = null
        clearAuth()
    }

    /**
     * Clear auth error to allow retry
     */
    const clearError = () => {
        authError.value = null
    }

    return {
        // State
        isAuthenticating: readonly(isAuthenticating),
        authError: readonly(authError),
        hasValidToken,

        // Methods
        authenticate,
        logout,
        clearError,
        refreshToken,
        refreshUserDetails
    }
}
