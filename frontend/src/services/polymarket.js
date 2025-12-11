import api from './api';

/**
 * Polymarket API service for frontend.
 * Handles market data fetching and trade logging.
 * Actual trading is done client-side via @polymarket/clob-client.
 */
export const polymarketApi = {
    /**
     * Get active 15-minute crypto markets (BTC/ETH up/down).
     * These are suitable for the gabagool arbitrage strategy.
     */
    getActiveMarkets: () => api.get('/polymarket/markets'),

    /**
     * Get markets with arbitrage opportunities.
     * Returns markets where combined price (YES + NO) < $1.00.
     */
    getArbitrageOpportunities: () => api.get('/polymarket/markets/arbitrage'),

    /**
     * Get order book for a specific token.
     * Useful for checking liquidity before trading.
     * @param {string} tokenId - The token ID to get order book for
     */
    getOrderBook: (tokenId) => api.get(`/polymarket/orderbook/${tokenId}`),

    /**
     * Get user's positions.
     */
    getPositions: () => api.get('/polymarket/positions'),

    /**
     * Log a trade executed on frontend.
     * This is for audit/history - no execution happens backend.
     * @param {Object} trade - Trade details
     * @param {string} trade.marketId - Market ID
     * @param {string} trade.side - 'YES' or 'NO'
     * @param {string} trade.orderType - 'BUY' or 'SELL'
     * @param {number} trade.shares - Number of shares
     * @param {number} trade.price - Price per share
     * @param {number} trade.totalCost - Total cost of trade
     * @param {string} trade.txHash - Transaction hash (optional)
     */
    logTrade: (trade) => api.post('/polymarket/trades/log', trade),

    /**
     * Health check for Polymarket integration.
     */
    healthCheck: () => api.get('/polymarket/health')
};

export default polymarketApi;
