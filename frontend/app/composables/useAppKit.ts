/**
 * AppKit (WalletConnect) composable
 * Provides wallet connection functionality
 */

const appKitInstance = ref(null)
const isInitialized = ref(false)
const isInitializing = ref(false)
const accountData = ref({ isConnected: false, address: null })

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
            window.__appKit = appKit

            // Subscribe to account changes
            if (appKit.subscribeAccount) {
                appKit.subscribeAccount((account) => {
                    accountData.value = account
                })
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
            appKit.open()
        } else {
            console.error('[AppKit] Failed to open - not initialized')
            alert('Wallet connection is not available. Please try again later.')
        }
    }

    return {
        appKit: appKitInstance,
        isInitialized,
        isInitializing,
        accountData,
        initAppKit,
        openModal
    }
}
