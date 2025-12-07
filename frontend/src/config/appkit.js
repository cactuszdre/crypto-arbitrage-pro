import { createAppKit } from '@reown/appkit/vue'
import { mainnet, arbitrum } from '@reown/appkit/networks'
import { WagmiAdapter } from '@reown/appkit-adapter-wagmi'

// 1. Get a project ID at https://cloud.reown.com
const projectId = import.meta.env.VITE_WALLETCONNECT_PROJECT_ID

console.log('AppKit Config - ProjectID loaded:', projectId ? 'YES (Length: ' + projectId.length + ')' : 'NO')

if (!projectId) {
    console.error('VITE_WALLETCONNECT_PROJECT_ID is not set in .env file')
}

// 2. Set up Wagmi Adapter
export const networks = [mainnet, arbitrum]

export const wagmiAdapter = new WagmiAdapter({
    projectId,
    networks
})

// 3. Create AppKit instance
createAppKit({
    adapters: [wagmiAdapter],
    networks,
    projectId,
    metadata: {
        name: 'CactusFund',
        description: 'Crypto Arbitrage & Funding Rate Analytics',
        url: 'https://localhost:5173',
        icons: ['https://avatars.githubusercontent.com/u/37784886']
    },
    themeMode: 'dark'
})
