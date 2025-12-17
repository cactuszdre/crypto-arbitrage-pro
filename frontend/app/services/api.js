/**
 * API Services for Nuxt
 * Uses $fetch (built-in) instead of axios
 * Same API interface as the original for easy migration
 */

const baseURL = '/api'

// Helper to wrap $fetch to return axios-like response
const apiFetch = async (url, options = {}) => {
    const data = await $fetch(url, {
        baseURL,
        ...options
    })
    return { data }
}

// DEX API
export const dexApi = {
    getAll: () => apiFetch('/dex'),
    getActive: () => apiFetch('/dex/active'),
    create: (data) => apiFetch('/dex', { method: 'POST', body: data }),
    update: (id, data) => apiFetch(`/dex/${id}`, { method: 'PUT', body: data }),
    toggle: (id, isActive) => apiFetch(`/dex/${id}/toggle?isActive=${isActive}`, { method: 'PATCH' }),
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
    getAll: () => apiFetch('/users'),
    getAdmins: () => apiFetch('/users/admins'),
    getByWallet: (address) => apiFetch(`/users/wallet/${address}`),
    updateRole: (id, role) => apiFetch(`/users/${id}/role?role=${role}`, { method: 'PATCH' }),
    updatePlan: (id, plan) => apiFetch(`/users/${id}/plan?plan=${plan}`, { method: 'PATCH' }),
    updateLastLogin: (address) => apiFetch(`/users/wallet/${address}/login`, { method: 'POST' }),
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
