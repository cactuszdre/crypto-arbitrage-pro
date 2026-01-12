// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: process.env.NODE_ENV === 'development' },

  // Use same port as Vite frontend (for script compatibility)
  devServer: {
    port: 5173,
  },

  // Nitro server configuration
  nitro: {
    // Reduce memory usage during build
    minify: false, // Disable minification to reduce memory
  },

  // Build configuration
  build: {
    transpile: ['@vuepic/vue-datepicker']
  },

  // Vite build optimization for low-memory environments
  vite: {
    build: {
      // Reduce memory usage
      sourcemap: false,
      // Use terser with reduced memory footprint
      minify: 'esbuild',
      // Build in chunks to reduce peak memory
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          // Manual chunking to reduce memory pressure
          manualChunks: {
            'vendor-three': ['three'],
            'vendor-ethers': ['ethers', 'viem'],
            'vendor-wagmi': ['wagmi', '@reown/appkit', '@reown/appkit-adapter-wagmi'],
            'vendor-chart': ['chart.js', 'vue-chartjs'],
          },
        },
      },
    },
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
        { name: 'description', content: 'Track live funding rates across top crypto DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { name: 'keywords', content: 'crypto, funding rates, arbitrage, cryptocurrency, trading, defi, perpetuals, perps, decentralized exchanges' },
        { name: 'author', content: 'CactusFund' },
        { name: 'theme-color', content: '#10b981' },
        // Open Graph
        { property: 'og:type', content: 'website' },
        { property: 'og:url', content: 'https://cactusfund.app/' },
        { property: 'og:title', content: 'CactusFund - Real-Time Crypto Funding Rates & Arbitrage Scanner' },
        { property: 'og:description', content: 'Track live funding rates across top crypto DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { property: 'og:image', content: 'https://cactusfund.app/logo_cactus_without-bg.png' },
        // Twitter
        { property: 'twitter:card', content: 'summary' },
        { property: 'twitter:url', content: 'https://cactusfund.app/' },
        { property: 'twitter:title', content: 'CactusFund - Real-Time Crypto Funding Rates & Arbitrage Scanner' },
        { property: 'twitter:description', content: 'Track live funding rates across top crypto DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.' },
        { property: 'twitter:image', content: 'https://cactusfund.app/logo_cactus_without-bg.png' },
      ],
      link: [
        // Favicon principal
        { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' },
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'icon', type: 'image/png', sizes: '96x96', href: '/favicon-96x96.png' },
        // Apple Touch Icon (pour iOS)
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        // Web App Manifest (pour Android/PWA)
        { rel: 'manifest', href: '/site.webmanifest' },
        // Note: Canonical URLs are now handled dynamically per page via useSeo composable
      ],
      script: [
        { src: 'https://code.iconify.design/3/3.1.0/iconify.min.js', defer: true },
        // Google Tag Manager - DataLayer initialization (must come first)
        {
          id: 'gtm-datalayer',
          innerHTML: `window.dataLayer = window.dataLayer || [];`,
          tagPosition: 'head',
        },
        // Google Tag Manager - Main script
        {
          id: 'gtm-script',
          src: `https://www.googletagmanager.com/gtm.js?id=GTM-PNMGDZMQ`,
          async: true,
        },
        // Google Tag Manager - gtm.js event push
        {
          id: 'gtm-start',
          innerHTML: `window.dataLayer.push({'gtm.start': new Date().getTime(), event: 'gtm.js'});`,
          tagPosition: 'head',
        },
      ],
      noscript: [
        // Google Tag Manager (noscript) - fallback for no-JS browsers
        {
          id: 'gtm-noscript',
          innerHTML: `<iframe src="https://www.googletagmanager.com/ns.html?id=GTM-PNMGDZMQ" height="0" width="0" style="display:none;visibility:hidden"></iframe>`,
          tagPosition: 'bodyOpen',
        },
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
