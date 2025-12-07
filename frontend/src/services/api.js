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

export default api;

