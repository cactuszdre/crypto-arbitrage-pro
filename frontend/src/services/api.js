import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json'
    }
});

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
    fetchAndSave: (symbol) => api.post(`/funding-rates/fetch/${symbol}`)
};

export const arbitrageApi = {
    getOpportunities: () => api.get('/arbitrage/opportunities'),
    calculate: (dex1, dex2) => api.get(`/arbitrage/calculate?dex1=${dex1}&dex2=${dex2}`)
};

export default api;
