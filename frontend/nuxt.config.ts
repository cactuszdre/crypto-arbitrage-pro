// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // Use same port as Vite frontend (for script compatibility)
  devServer: {
    port: 5173,
  },

  // Modules
  modules: [
    '@nuxtjs/tailwindcss',
  ],

  // Runtime Config (environment variables)
  runtimeConfig: {
    // Private keys (server-side only)
    backendUrl: process.env.BACKEND_URL || 'http://localhost:8080',

    // Public keys (exposed to client)
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api',
      walletConnectProjectId: process.env.NUXT_PUBLIC_WALLETCONNECT_PROJECT_ID || '',
    }
  },

  // App configuration
  app: {
    head: {
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      title: 'CactusFund',
      meta: [
        { name: 'description', content: 'Track live funding rates across Paradex, Hyperliquid, and Lighter DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { name: 'keywords', content: 'crypto, funding rates, arbitrage, cryptocurrency, trading, defi, perpetuals, perps, hyperliquid, paradex, lighter' },
        { name: 'author', content: 'CactusFund' },
        { name: 'theme-color', content: '#10b981' },
        // Open Graph
        { property: 'og:type', content: 'website' },
        { property: 'og:url', content: 'https://cactusfund.app/' },
        { property: 'og:title', content: 'CactusFund - Real-Time Crypto Funding Rates & Arbitrage Scanner' },
        { property: 'og:description', content: 'Track live funding rates across Paradex, Hyperliquid, and Lighter DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { property: 'og:image', content: 'https://cactusfund.app/logo_cactus_without-bg.png' },
        // Twitter
        { property: 'twitter:card', content: 'summary' },
        { property: 'twitter:url', content: 'https://cactusfund.app/' },
        { property: 'twitter:title', content: 'CactusFund - Real-Time Crypto Funding Rates & Arbitrage Scanner' },
        { property: 'twitter:description', content: 'Track live funding rates across Paradex, Hyperliquid, and Lighter DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { property: 'twitter:image', content: 'https://cactusfund.app/logo_cactus_without-bg.png' },
      ],
      link: [
        { rel: 'icon', type: 'image/png', href: '/logo_cactus_without-bg.png' },
        { rel: 'canonical', href: 'https://cactusfund.app/' },
      ],
      script: [
        { src: 'https://code.iconify.design/3/3.1.0/iconify.min.js', defer: true },
      ],
    },
  },

  // CSS
  css: [
    '~/assets/css/main.css',
  ],

  // TypeScript
  typescript: {
    strict: true,
    shim: false,
  },

  // Vue configuration
  vue: {
    compilerOptions: {
      isCustomElement: (tag: string) => tag.startsWith('appkit-'),
    },
  },
})
