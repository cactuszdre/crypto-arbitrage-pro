import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json'
    }
});

// Intercepteur pour ajouter le wallet address aux requêtes
api.interceptors.request.use(
    config => {
        // Get user from localStorage and add wallet to headers
        const userStr = localStorage.getItem('user');
        if (userStr) {
            try {
                const user = JSON.parse(userStr);
                if (user?.walletAddress) {
                    config.headers['X-Wallet-Address'] = user.walletAddress;
                }
            } catch (e) {
                // Ignore parse errors
            }
        }
        return config;
    },
    error => Promise.reject(error)
);

// Intercepteur pour gérer les erreurs
api.interceptors.response.use(
    response => response,
    error => {
        console.error('API Error:', error.response?.data || error.message);
        return Promise.reject(error);
    }
);

export const authApi = {
    login: (username, password) => api.post('/auth/login', { username, password }),
    register: (username, password) => api.post('/auth/register', { username, password })
};

export const dexApi = {
    getAll: () => api.get('/dex'),
    getActive: () => api.get('/dex/active'),
    getByName: (name) => api.get(`/dex/${name}`),
    create: (dexData) => api.post('/dex', dexData)
};

export const fundingRateApi = {
    getAll: () => api.get('/funding-rates'),
    getByDex: (dexName) => api.get(`/funding-rates/dex/${dexName}`),
    getLatest: (dexName, symbol) => api.get(`/funding-rates/dex/${dexName}/symbol/${symbol}`),
    getHistory: (params) => api.get('/funding-rates/history', { params }),
    getSymbols: (dexName) => api.get(`/funding-rates/symbols/${dexName}`),
    getAllSymbols: () => api.get('/funding-rates/symbols'),
    fetchAndSave: (symbol) => api.post(`/funding-rates/fetch/${symbol}`)
};

export const arbitrageApi = {
    getOpportunities: () => api.get('/arbitrage/opportunities'),
    getHistoricalOpportunities: (days) => api.get('/arbitrage/opportunities/historical', { params: { days } }),
    // New endpoints for raw rates (for frontend-side arbitrage calculation)
    getAllRates: () => api.get('/arbitrage/rates/all'),
    getHistoricalRates: (days) => api.get('/arbitrage/rates/historical', { params: { days } }),
    calculate: (dex1, dex2) => api.get(`/arbitrage/calculate?dex1=${dex1}&dex2=${dex2}`)
};

export const userApi = {
    getAll: () => api.get('/users'),
    getAdmins: () => api.get('/users/admins'),
    getByWallet: (walletAddress) => api.get(`/users/wallet/${walletAddress}`),
    updateLastLogin: (walletAddress) => api.post(`/users/wallet/${walletAddress}/login`),
    addAdmin: (walletAddress) => api.post('/users/admins', { walletAddress }),
    removeAdmin: (walletAddress) => api.delete(`/users/admins/${walletAddress}`),
    deleteUser: (id) => api.delete(`/users/${id}`)
};

export const cryptoPriceApi = {
    getAll: () => api.get('/crypto-prices')
};

// Chart API - Pre-aggregated 5-minute data for charts
export const chartApi = {
    // Get chart data for a specific symbol and DEX
    getChartData: (symbol, dex, hours = 24) =>
        api.get(`/charts/funding-rate/${symbol}/${dex}`, { params: { hours } }),

    // Get chart data for a symbol across all DEXs
    getChartDataBySymbol: (symbol, hours = 24) =>
        api.get(`/charts/funding-rate/${symbol}`, { params: { hours } }),

    // Get chart data for all symbols on a specific DEX
    getChartDataByDex: (dex, hours = 24) =>
        api.get(`/charts/funding-rate/dex/${dex}`, { params: { hours } }),

    // Get latest N data points for real-time updates
    getLatestDataPoints: (symbol, dex, count = 12) =>
        api.get(`/charts/funding-rate/${symbol}/${dex}/latest`, { params: { count } }),

    // Get comparison data across DEXs
    getComparisonData: (symbol, hours = 24) =>
        api.get(`/charts/funding-rate/${symbol}/compare`, { params: { hours } }),

    // Get daily comparison data for historical views (7D, 30D)
    getDailyComparisonData: (symbol, days = 7) =>
        api.get(`/charts/funding-rate/${symbol}/compare-daily`, { params: { days } }),

    // Get statistics summary
    getStatsSummary: (symbol, hours = 24) =>
        api.get(`/charts/funding-rate/${symbol}/stats`, { params: { hours } })
};

export const chatApi = {
    // Send message to AI assistant
    sendMessage: (messages) => api.post('/chat', { messages })
};

export default api;

