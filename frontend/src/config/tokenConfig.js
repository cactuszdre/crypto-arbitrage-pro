// Token and chain configuration for Swap feature
// Easy to extend by adding more tokens/chains here

export const CHAINS = {
    1: {
        name: 'Ethereum',
        symbol: 'ETH',
        explorer: 'https://etherscan.io',
        iconUrl: 'https://cryptologos.cc/logos/ethereum-eth-logo.svg'
    },
    // Future chains can be added here
    // 42161: { name: 'Arbitrum', symbol: 'ETH', explorer: 'https://arbiscan.io' },
}

export const TOKENS = {
    1: { // Ethereum Mainnet (chainId: 1)
        ETH: {
            symbol: 'ETH',
            name: 'Ether',
            decimals: 18,
            isNative: true,
            iconUrl: 'https://cryptologos.cc/logos/ethereum-eth-logo.svg'
        },
        WETH: {
            symbol: 'WETH',
            name: 'Wrapped Ether',
            decimals: 18,
            address: '0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2',
            iconUrl: 'https://cryptologos.cc/logos/ethereum-eth-logo.svg'
        },
    },
}

// WETH contract ABI (only the functions we need)
export const WETH_ABI = [
    'function deposit() payable',
    'function withdraw(uint256 wad)',
    'function balanceOf(address owner) view returns (uint256)',
    'function approve(address spender, uint256 amount) returns (bool)',
    'function allowance(address owner, address spender) view returns (uint256)',
]

export const DEFAULT_CHAIN_ID = 1
