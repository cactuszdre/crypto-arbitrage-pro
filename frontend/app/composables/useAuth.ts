/**
 * Auth composable - manages user authentication state
 * Migrated from stores/auth.js
 */
export const useAuth = () => {
    // State stored in useState for SSR compatibility
    const user = useState<{
        walletAddress?: string
        role?: string
        plan?: string
    } | null>('auth-user', () => null)

    const isAuthenticated = computed(() => !!user.value?.walletAddress)
    const isLoading = ref(false)

    // Initialize from localStorage on client
    if (import.meta.client) {
        const storedUser = localStorage.getItem('user')
        if (storedUser) {
            try {
                user.value = JSON.parse(storedUser)
            } catch (e) {
                console.error('Failed to parse stored user:', e)
            }
        }
    }

    /**
     * Set user data after successful authentication
     */
    function setUser(userData: typeof user.value) {
        user.value = userData
        if (import.meta.client) {
            if (userData) {
                localStorage.setItem('user', JSON.stringify(userData))
            } else {
                localStorage.removeItem('user')
            }
        }
    }

    /**
     * Clear user data (logout)
     */
    function logout() {
        user.value = null
        if (import.meta.client) {
            localStorage.removeItem('user')
        }
    }

    /**
     * Check if current user is admin
     */
    function isAdmin() {
        return user.value?.role === 'ADMIN'
    }

    /**
     * Check if current user has Pro plan
     */
    function isPro() {
        return user.value?.plan === 'PRO'
    }

    /**
     * Get wallet address (shortened for display)
     */
    function getShortWallet() {
        const wallet = user.value?.walletAddress
        if (!wallet) return ''
        return `${wallet.slice(0, 6)}...${wallet.slice(-4)}`
    }

    return {
        state: readonly({ user, isAuthenticated, isLoading }),
        user: readonly(user),
        isAuthenticated,
        setUser,
        logout,
        isAdmin,
        isPro,
        getShortWallet
    }
}
