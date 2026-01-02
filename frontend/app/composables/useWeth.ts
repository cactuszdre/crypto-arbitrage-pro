/**
 * WETH (Wrapped Ether) composable
 * Provides ETH/WETH swap functionality using viem
 */

import { parseEther, formatEther, createPublicClient, createWalletClient, custom, http } from 'viem'
import { mainnet } from 'viem/chains'

// WETH contract on Ethereum mainnet
const WETH_ADDRESS = '0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2' as const

// WETH ABI - only the functions we need
const WETH_ABI = [
    {
        name: 'deposit',
        type: 'function',
        stateMutability: 'payable',
        inputs: [],
        outputs: []
    },
    {
        name: 'withdraw',
        type: 'function',
        stateMutability: 'nonpayable',
        inputs: [{ name: 'wad', type: 'uint256' }],
        outputs: []
    },
    {
        name: 'balanceOf',
        type: 'function',
        stateMutability: 'view',
        inputs: [{ name: 'account', type: 'address' }],
        outputs: [{ type: 'uint256' }]
    }
] as const

export const useWeth = () => {
    // Create public client for read operations
    const getPublicClient = () => {
        return createPublicClient({
            chain: mainnet,
            transport: http()
        })
    }

    // Create wallet client for write operations (requires connected wallet)
    const getWalletClient = async () => {
        if (typeof window === 'undefined' || !window.ethereum) {
            throw new Error('No wallet provider found')
        }

        // Request account access first
        const provider = window.ethereum as Parameters<typeof custom>[0]
        await (provider as { request: (args: { method: string }) => Promise<unknown> }).request({ method: 'eth_requestAccounts' })

        return createWalletClient({
            chain: mainnet,
            transport: custom(provider)
        })
    }

    /**
     * Get ETH balance for an address
     */
    const getEthBalance = async (address: string): Promise<string> => {
        try {
            const client = getPublicClient()
            const balance = await client.getBalance({
                address: address as `0x${string}`
            })
            return formatEther(balance)
        } catch (error) {
            console.error('Failed to fetch ETH balance:', error)
            return '0'
        }
    }

    /**
     * Get WETH balance for an address
     */
    const getWethBalance = async (address: string): Promise<string> => {
        try {
            const client = getPublicClient()
            const balance = await client.readContract({
                address: WETH_ADDRESS,
                abi: WETH_ABI,
                functionName: 'balanceOf',
                args: [address as `0x${string}`]
            })
            return formatEther(balance as bigint)
        } catch (error) {
            console.error('Failed to fetch WETH balance:', error)
            return '0'
        }
    }

    /**
     * Wrap ETH to WETH (deposit)
     * @param amount - Amount of ETH to wrap
     * @param userAddress - Connected wallet address
     */
    const wrapEth = async (amount: string, userAddress: string): Promise<{ hash: string; wait: () => Promise<void> }> => {
        if (!userAddress) {
            throw new Error('No account connected')
        }

        // Ensure amount is a valid string
        const amountStr = String(amount).trim()
        if (!amountStr || isNaN(parseFloat(amountStr))) {
            throw new Error('Invalid amount')
        }

        const walletClient = await getWalletClient()
        const account = userAddress as `0x${string}`
        const value = parseEther(amountStr)

        const hash = await walletClient.writeContract({
            address: WETH_ADDRESS,
            abi: WETH_ABI,
            functionName: 'deposit',
            args: [],
            value,
            account
        })

        // Return object compatible with existing code
        return {
            hash,
            wait: async () => {
                const publicClient = getPublicClient()
                await publicClient.waitForTransactionReceipt({ hash })
            }
        }
    }

    /**
     * Unwrap WETH to ETH (withdraw)
     * @param amount - Amount of WETH to unwrap
     * @param userAddress - Connected wallet address
     */
    const unwrapWeth = async (amount: string, userAddress: string): Promise<{ hash: string; wait: () => Promise<void> }> => {
        if (!userAddress) {
            throw new Error('No account connected')
        }

        // Ensure amount is a valid string
        const amountStr = String(amount).trim()
        if (!amountStr || isNaN(parseFloat(amountStr))) {
            throw new Error('Invalid amount')
        }

        const walletClient = await getWalletClient()
        const account = userAddress as `0x${string}`
        const value = parseEther(amountStr)

        const hash = await walletClient.writeContract({
            address: WETH_ADDRESS,
            abi: WETH_ABI,
            functionName: 'withdraw',
            args: [value],
            account
        })

        // Return object compatible with existing code
        return {
            hash,
            wait: async () => {
                const publicClient = getPublicClient()
                await publicClient.waitForTransactionReceipt({ hash })
            }
        }
    }

    /**
     * Get Etherscan transaction URL
     */
    const getExplorerTxUrl = (hash: string): string => {
        return `https://etherscan.io/tx/${hash}`
    }

    return {
        getEthBalance,
        getWethBalance,
        wrapEth,
        unwrapWeth,
        getExplorerTxUrl,
        WETH_ADDRESS
    }
}
