/**
 * Auth composable - manages user authentication state
 * Works with JWT tokens for secure authentication
 */

const TOKEN_KEY = 'auth_token'
const USER_KEY = 'auth_user'

// Global state (shared across components)
const user = ref<{
    walletAddress?: string
    role?: string
    plan?: string
} | null>(null)

const token = ref<string | null>(null)
const isLoading = ref(false)

export const useAuth = () => {
    const isAuthenticated = computed(() => !!user.value?.walletAddress && !!token.value)
    const isAdmin = computed(() => user.value?.role === 'ADMIN')
    const isPro = computed(() => user.value?.plan === 'PRO')

    // Initialize from localStorage on client
    if (import.meta.client && user.value === null) {
        const storedUser = localStorage.getItem(USER_KEY)
        const storedToken = localStorage.getItem(TOKEN_KEY)

        if (storedUser) {
            try {
                user.value = JSON.parse(storedUser)
            } catch (e) {
                console.error('[Auth] Failed to parse stored user:', e)
            }
        }

        if (storedToken) {
            token.value = storedToken
        }
    }

    /**
     * Set user data after successful authentication
     */
    function setUser(userData: typeof user.value, authToken?: string) {
        user.value = userData

        if (import.meta.client) {
            if (userData) {
                localStorage.setItem(USER_KEY, JSON.stringify(userData))
            } else {
                localStorage.removeItem(USER_KEY)
            }

            if (authToken) {
                token.value = authToken
                localStorage.setItem(TOKEN_KEY, authToken)
            }
        }
    }

    /**
     * Set the JWT token
     */
    function setToken(authToken: string) {
        token.value = authToken
        if (import.meta.client) {
            localStorage.setItem(TOKEN_KEY, authToken)
        }
    }

    /**
     * Get the current JWT token
     */
    function getToken(): string | null {
        return token.value
    }

    /**
     * Clear user data (logout)
     */
    function logout() {
        user.value = null
        token.value = null

        if (import.meta.client) {
            localStorage.removeItem(USER_KEY)
            localStorage.removeItem(TOKEN_KEY)
        }
    }

    /**
     * Get wallet address (shortened for display)
     */
    function getShortWallet(): string {
        const wallet = user.value?.walletAddress
        if (!wallet) return ''
        return `${wallet.slice(0, 6)}...${wallet.slice(-4)}`
    }

    /**
     * Check if current user has the specified role
     */
    function hasRole(role: string): boolean {
        return user.value?.role === role
    }

    return {
        // State (readonly to prevent direct mutation)
        state: readonly({ user, isAuthenticated, isLoading, isAdmin, isPro }),
        user: readonly(user),
        token: readonly(token),
        isAuthenticated,
        isAdmin,
        isPro,

        // Methods
        setUser,
        setToken,
        getToken,
        logout,
        getShortWallet,
        hasRole
    }
}
