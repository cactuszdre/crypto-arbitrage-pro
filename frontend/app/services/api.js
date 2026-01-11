/**
 * API Services for Nuxt
 * Uses $fetch (built-in) instead of axios
 * Same API interface as the original for easy migration
 * 
 * Includes automatic JWT token injection for authenticated requests
 */

const baseURL = '/api'
const TOKEN_KEY = 'auth_token'

/**
 * Get the JWT token from localStorage
 */
const getAuthToken = () => {
    if (typeof window !== 'undefined') {
        return localStorage.getItem(TOKEN_KEY)
    }
    return null
}

/**
 * Helper to wrap $fetch to return axios-like response
 * Automatically includes JWT token if available
 */
const apiFetch = async (url, options = {}) => {
    const token = getAuthToken()

    // Build headers with optional auth token
    const headers = {
        ...options.headers
    }

    if (token) {
        headers['Authorization'] = `Bearer ${token}`
    }

    const data = await $fetch(url, {
        baseURL,
        ...options,
        headers
    })
    return { data }
}

/**
 * Authenticated fetch - throws if no token available
 * Use this for endpoints that REQUIRE authentication
 */
const authFetch = async (url, options = {}) => {
    const token = getAuthToken()

    if (!token) {
        throw new Error('Authentication required. Please connect your wallet.')
    }

    return apiFetch(url, options)
}

// DEX API
export const dexApi = {
    getAll: () => apiFetch('/dex'),
    getActive: () => apiFetch('/dex/active'),
    create: (data) => authFetch('/dex', { method: 'POST', body: data }),
    update: (id, data) => authFetch(`/dex/${id}`, { method: 'PUT', body: data }),
    toggle: (id, isActive) => authFetch(`/dex/${id}/toggle?isActive=${isActive}`, { method: 'PATCH' }),
    syncMarkets: () => authFetch('/dex/sync-markets', { method: 'POST' }),
    reactivateMarkets: (dexName) => authFetch(`/dex/${dexName}/reactivate-markets`, { method: 'POST' }),
}

// Arbitrage API
export const arbitrageApi = {
    getOpportunities: () => apiFetch('/opportunites/opportunities'),
    getAllRates: () => apiFetch('/opportunites/rates'),
    getHistoricalOpportunities: (days) => apiFetch(`/opportunites/history?days=${days}`),
    getHistoricalRates: (days) => apiFetch(`/opportunites/rates-history?days=${days}`),
}

// User API
export const userApi = {
    getAll: () => authFetch('/users'),
    getAdmins: () => authFetch('/users/admins'),
    getByWallet: (address) => apiFetch(`/users/wallet/${address}`),
    updateRole: (id, role) => authFetch(`/users/${id}/role?role=${role}`, { method: 'PATCH' }),
    updatePlan: (id, plan) => authFetch(`/users/${id}/plan?plan=${plan}`, { method: 'PATCH' }),
    updateLastLogin: (address) => apiFetch(`/users/wallet/${address}/login`, { method: 'POST' }),
    addAdmin: (walletAddress) => authFetch('/users/admins', {
        method: 'POST',
        body: { walletAddress }
    }),
    removeAdmin: (walletAddress) => authFetch(`/users/admins/${walletAddress}`, {
        method: 'DELETE'
    }),
}

// Chart API
export const chartApi = {
    getFundingRateHistory: (symbol, dex, days = 7) =>
        apiFetch(`/charts/funding-rates?symbol=${symbol}&dex=${dex}&days=${Math.round(days)}`),
    getSpreadHistory: (symbol, days = 7) =>
        apiFetch(`/charts/spread?symbol=${symbol}&days=${Math.round(days)}`),
    getComparisonData: (symbol, hours = 24) =>
        apiFetch(`/charts/comparison?symbol=${symbol}&hours=${Math.round(hours)}`),
    getHourlyComparisonData: (symbol, hours = 168) =>
        apiFetch(`/charts/comparison/hourly?symbol=${symbol}&hours=${Math.round(hours)}`),
    getDailyComparisonData: (symbol, days = 30) =>
        apiFetch(`/charts/comparison/daily?symbol=${symbol}&days=${Math.round(days)}`),
    // Admin endpoints
    getAdminStats: () => authFetch('/charts/admin/stats'),
    triggerAggregation: () => authFetch('/charts/admin/aggregate', { method: 'POST' }),
}

// Funding Rate API
export const fundingRateApi = {
    getAll: () => apiFetch('/funding-rates'),
    getAllSymbols: () => apiFetch('/funding-rates/symbols'),
    getHistory: (params) => {
        // Ensure hours is an integer
        const sanitizedParams = { ...params }
        if (sanitizedParams.hours) {
            sanitizedParams.hours = Math.round(Number(sanitizedParams.hours))
        }
        const query = new URLSearchParams(sanitizedParams).toString()
        return apiFetch(`/funding-rates/history?${query}`)
    },
}

// Chat API
export const chatApi = {
    sendMessage: (messages) =>
        apiFetch('/chat', { method: 'POST', body: messages }),
}

// Auth API (for secure wallet authentication)
export const authApi = {
    getNonce: (walletAddress) => apiFetch(`/auth/nonce/${walletAddress}`),
    verify: (walletAddress, signature, nonce) => apiFetch('/auth/verify', {
        method: 'POST',
        body: { walletAddress, signature, nonce }
    }),
    refresh: () => authFetch('/auth/refresh', { method: 'POST' }),
    me: () => authFetch('/auth/me'),
}
