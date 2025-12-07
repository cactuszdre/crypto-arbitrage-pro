/**
 * Arbitrage Execution Services - Open Source Edition
 * 
 * This file contains stub implementations for trade execution.
 * Full execution capabilities are only available in the Pro version.
 * 
 * Get the Pro version at: https://github.com/cactuszdre/crypto-arbitrage-pro
 */

const PRO_ERROR_MESSAGE = `
🔒 Cette fonctionnalité nécessite la version Pro.

L'exécution automatique des trades d'arbitrage n'est pas disponible 
dans la version open source.

👉 Obtenez la version Pro : https://github.com/cactuszdre/crypto-arbitrage-pro
`.trim()

/**
 * Hyperliquid Order Execution (Pro Only)
 */
export const executeHyperliquidOrder = async (walletProvider, orderDetails) => {
    console.warn('[Hyperliquid] Execution not available in open source version')
    throw new Error(PRO_ERROR_MESSAGE)
}

/**
 * Paradex Order Execution (Pro Only)
 */
export const executeParadexOrder = async (walletProvider, orderDetails) => {
    console.warn('[Paradex] Execution not available in open source version')
    throw new Error(PRO_ERROR_MESSAGE)
}

/**
 * Lighter.xyz Order Execution (Pro Only)
 */
export const executeLighterOrder = async (walletProvider, orderDetails) => {
    console.warn('[Lighter] Execution not available in open source version')
    throw new Error(PRO_ERROR_MESSAGE)
}
