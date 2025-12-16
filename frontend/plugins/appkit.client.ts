/**
 * AppKit (WalletConnect) plugin - Client-only
 * Initializes wallet connection functionality
 */
export default defineNuxtPlugin(async (nuxtApp) => {
    // Only run on client
    if (!import.meta.client) return

    const config = useRuntimeConfig()
    const projectId = config.public.walletConnectProjectId

    if (!projectId) {
        console.warn('NUXT_PUBLIC_WALLETCONNECT_PROJECT_ID is not set')
        return
    }

    try {
        // Dynamic import to avoid SSR issues
        const { createAppKit } = await import('@reown/appkit/vue')
        const { WagmiAdapter } = await import('@reown/appkit-adapter-wagmi')
        const { mainnet, arbitrum } = await import('@reown/appkit/networks')

        // Set up Wagmi Adapter
        const networks = [mainnet, arbitrum]

        const wagmiAdapter = new WagmiAdapter({
            projectId,
            networks
        })

        // Create AppKit instance
        const appKit = createAppKit({
            adapters: [wagmiAdapter],
            networks,
            projectId,
            metadata: {
                name: 'CactusFund',
                description: 'Crypto Arbitrage & Funding Rate Analytics',
                url: typeof window !== 'undefined' ? window.location.origin : 'https://cactusfund.app',
                icons: ['https://cactusfund.app/logo_cactus_without-bg.png']
            },
            themeMode: 'dark'
        })

            // Store reference globally for Header component
            ; (window as any).__appKit = appKit

        // Provide to app
        nuxtApp.provide('appKit', appKit)
        nuxtApp.provide('wagmiAdapter', wagmiAdapter)

        console.log('✅ AppKit initialized successfully')
    } catch (error) {
        console.error('Failed to initialize AppKit:', error)
    }
})
