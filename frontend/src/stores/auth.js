import { reactive, readonly } from 'vue'

/**
 * Simple reactive auth store for user state management.
 * Stores user info from wallet authentication.
 */
const state = reactive({
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    isAuthenticated: false,
    isLoading: false
})

// Initialize authentication state
state.isAuthenticated = !!state.user?.walletAddress

/**
 * Set user data after successful authentication
 */
function setUser(userData) {
    state.user = userData
    state.isAuthenticated = !!userData
    if (userData) {
        localStorage.setItem('user', JSON.stringify(userData))
    } else {
        localStorage.removeItem('user')
    }
}

/**
 * Clear user data (logout)
 */
function logout() {
    state.user = null
    state.isAuthenticated = false
    localStorage.removeItem('user')
}

/**
 * Check if current user is admin
 */
function isAdmin() {
    return state.user?.role === 'ADMIN'
}

/**
 * Check if current user has Pro plan
 */
function isPro() {
    return state.user?.plan === 'PRO'
}

/**
 * Get wallet address (shortened for display)
 */
function getShortWallet() {
    const wallet = state.user?.walletAddress
    if (!wallet) return ''
    return `${wallet.slice(0, 6)}...${wallet.slice(-4)}`
}

export const useAuth = () => ({
    state: readonly(state),
    setUser,
    logout,
    isAdmin,
    isPro,
    getShortWallet
})
