/**
 * AppKit (WalletConnect) composable
 * Provides wallet connection functionality
 */

const appKitInstance = ref(null)
const isInitialized = ref(false)
const isInitializing = ref(false)
const accountData = ref({ isConnected: false, address: null })
const wagmiAdapterState = ref(null) // Stores the Wagmi Adapter for signing

export const useAppKit = () => {
    const config = useRuntimeConfig()

    const initAppKit = async () => {
        // Already initialized or initializing
        if (isInitialized.value || isInitializing.value) {
            return appKitInstance.value
        }

        // Only run on client
        if (typeof window === 'undefined') {
            return null
        }

        const projectId = config.public.walletConnectProjectId

        if (!projectId) {
            console.warn('[AppKit] NUXT_PUBLIC_WALLETCONNECT_PROJECT_ID is not set')
            return null
        }

        isInitializing.value = true

        try {
            console.log('[AppKit] Initializing...')

            // Dynamic imports
            const [appkitVue, appkitAdapter, appkitNetworks] = await Promise.all([
                import('@reown/appkit/vue'),
                import('@reown/appkit-adapter-wagmi'),
                import('@reown/appkit/networks')
            ])

            const { createAppKit } = appkitVue
            const { WagmiAdapter } = appkitAdapter
            const { mainnet, arbitrum } = appkitNetworks

            console.log('[AppKit] Modules loaded')

            // Set up networks
            const networks = [mainnet, arbitrum]

            // Create Wagmi Adapter
            const wagmiAdapter = new WagmiAdapter({
                projectId,
                networks
            })

            // Store wagmi adapter for signing operations
            wagmiAdapterState.value = wagmiAdapter

            // Create AppKit
            const appKit = createAppKit({
                adapters: [wagmiAdapter],
                networks,
                projectId,
                metadata: {
                    name: 'CactusFund',
                    description: 'Crypto Arbitrage & Funding Rate Analytics',
                    url: window.location.origin,
                    icons: ['https://cactusfund.app/logo_cactus_without-bg.png']
                },
                themeMode: 'dark'
            })

            // Store instance
            appKitInstance.value = appKit
            if (typeof window !== 'undefined') {
                (window as any).__appKit = appKit
            }

            // Subscribe to account changes
            if (appKit.subscribeAccount) {
                appKit.subscribeAccount((account: any) => {
                    console.log('[AppKit] Account update:', account)
                    accountData.value = account
                })
            }
            
            // Get initial account state (for page refresh scenarios)
            try {
                if (appKit.getAccount) {
                    const currentAccount = appKit.getAccount()
                    console.log('[AppKit] Initial account state:', currentAccount)
                    if (currentAccount) {
                        accountData.value = currentAccount
                    }
                }
            } catch (e) {
                console.log('[AppKit] Could not get initial account state')
            }

            isInitialized.value = true
            console.log('✅ [AppKit] Initialized successfully')

            return appKit
        } catch (error) {
            console.error('[AppKit] Failed to initialize:', error)
            return null
        } finally {
            isInitializing.value = false
        }
    }

    const openModal = async () => {
        let appKit = appKitInstance.value

        // Try to initialize if not already
        if (!appKit) {
            appKit = await initAppKit()
        }

        if (appKit?.open) {
            // If already connected, open Account view instead of Connect view
            if (accountData.value?.isConnected) {
                console.log('[AppKit] Already connected, opening Account view')
                appKit.open({ view: 'Account' })
            } else {
                appKit.open()
            }
        } else {
            console.error('[AppKit] Failed to open - not initialized')
            alert('Wallet connection is not available. Please try again later.')
        }
    }
    
    // Disconnect wallet
    const disconnect = async () => {
        const appKit = appKitInstance.value
        if (appKit?.disconnect) {
            await appKit.disconnect()
            accountData.value = { isConnected: false, address: null }
            console.log('[AppKit] Disconnected')
        }
    }

    return {
        appKit: appKitInstance,
        isInitialized,
        isInitializing,
        accountData,
        wagmiAdapter: wagmiAdapterState,
        initAppKit,
        openModal,
        disconnect
    }
}
