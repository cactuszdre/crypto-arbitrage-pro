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
    const { setUser, setToken, getToken, logout: clearAuth, user, token } = useAuth()
    const { accountData } = useAppKit()

    /**
     * Check if we have a valid token
     */
    const hasValidToken = computed(() => !!getToken())

    /**
     * Request a nonce from the backend
     */
    const requestNonce = async (walletAddress: string): Promise<{ nonce: string; message: string }> => {
        const response = await $fetch<{ nonce: string; message: string }>(
            `/api/auth/nonce/${walletAddress}`
        )
        return response
    }

    /**
     * Sign a message using the connected wallet
     * Uses window.ethereum directly (works with MetaMask, WalletConnect, etc.)
     */
    const signMessage = async (message: string, walletAddress: string): Promise<string> => {
        // Use window.ethereum for signing (works with MetaMask, WalletConnect, etc.)
        const ethereum = (window as any).ethereum
        if (!ethereum) {
            throw new Error('No Ethereum provider found. Please install MetaMask or connect via WalletConnect.')
        }

        try {
            // Use personal_sign (EIP-191)
            const signature = await ethereum.request({
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
    const authenticate = async (walletAddress: string): Promise<boolean> => {
        isAuthenticating.value = true
        authError.value = null

        try {
            console.log('[SecureAuth] Starting authentication for:', walletAddress)

            // Step 1: Request nonce
            console.log('[SecureAuth] Requesting nonce...')
            const { nonce, message } = await requestNonce(walletAddress)
            console.log('[SecureAuth] Got nonce, requesting signature...')

            // Step 2: Sign the message
            const signature = await signMessage(message, walletAddress)
            console.log('[SecureAuth] Got signature, verifying...')

            // Step 3: Verify with backend
            const { token, role, isAdmin, plan } = await verifyAndGetToken(walletAddress, signature, nonce)
            console.log('[SecureAuth] Authentication successful! Role:', role)

            // Step 4: Store token and user
            setUser({
                walletAddress,
                role,
                plan: plan || 'FREE'
            }, token)

            return true
        } catch (error: any) {
            console.error('[SecureAuth] Authentication failed:', error)
            authError.value = error.message || 'Authentication failed'

            // Clear any existing auth
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

    return {
        // State
        isAuthenticating: readonly(isAuthenticating),
        authError: readonly(authError),
        hasValidToken,

        // Methods
        authenticate,
        logout,
        refreshToken,
        refreshUserDetails
    }
}
