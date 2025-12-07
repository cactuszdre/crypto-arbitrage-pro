// Swap service for ETH <-> WETH wrap/unwrap
import { ethers } from 'ethers'
import { TOKENS, WETH_ABI, DEFAULT_CHAIN_ID } from '../config/tokenConfig'

/**
 * Get balance for any configured token
 */
export async function getTokenBalance(provider, tokenSymbol, address, chainId = DEFAULT_CHAIN_ID) {
    try {
        const tokenConfig = TOKENS[chainId]?.[tokenSymbol]
        if (!tokenConfig) throw new Error(`Token ${tokenSymbol} not configured for chain ${chainId}`)

        if (tokenConfig.isNative) {
            const balance = await provider.getBalance(address)
            return ethers.formatUnits(balance, tokenConfig.decimals)
        } else {
            const tokenContract = new ethers.Contract(tokenConfig.address, WETH_ABI, provider)
            const balance = await tokenContract.balanceOf(address)
            return ethers.formatUnits(balance, tokenConfig.decimals)
        }
    } catch (error) {
        console.error(`Failed to get ${tokenSymbol} balance:`, error)
        return '0'
    }
}

/**
 * Execute swap between two tokens
 * Currently supports Wrap/Unwrap logic
 */
export async function executeSwap(provider, fromSymbol, toSymbol, amount, chainId = DEFAULT_CHAIN_ID) {
    const signer = await provider.getSigner()
    const fromToken = TOKENS[chainId]?.[fromSymbol]
    const toToken = TOKENS[chainId]?.[toSymbol]

    if (!fromToken || !toToken) throw new Error('Invalid tokens')

    const amountWei = ethers.parseUnits(amount.toString(), fromToken.decimals)

    // Case 1: Wrap (Native -> Wrapped)
    if (fromToken.isNative && toToken.symbol === 'WETH') { // In future check if toToken is wrapped version of fromToken
        const wethContract = new ethers.Contract(toToken.address, WETH_ABI, signer)
        const tx = await wethContract.deposit({ value: amountWei })
        return {
            hash: tx.hash,
            wait: () => tx.wait()
        }
    }

    // Case 2: Unwrap (Wrapped -> Native)
    if (fromToken.symbol === 'WETH' && toToken.isNative) {
        const wethContract = new ethers.Contract(fromToken.address, WETH_ABI, signer)
        const tx = await wethContract.withdraw(amountWei)
        return {
            hash: tx.hash,
            wait: () => tx.wait()
        }
    }

    throw new Error(`Swap pair ${fromSymbol} -> ${toSymbol} not supported`)
}

/**
 * Get transaction explorer URL
 */
export function getExplorerTxUrl(txHash, chainId = DEFAULT_CHAIN_ID) {
    const explorer = chainId === 1 ? 'https://etherscan.io' : 'https://etherscan.io'
    return `${explorer}/tx/${txHash}`
}
