<script setup>
const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const { setUser, logout, state } = useAuth()

// Check if on overview page
const isOverviewPage = computed(() => route.path === '/')

// Real-time clock
const currentTime = ref(new Date())
let clockInterval = null

// Crypto prices
const cryptoPrices = ref([])
const pricesLoading = ref(true)
let priceInterval = null

// OS Detection for keyboard shortcut
const isMac = ref(false)

// AppKit (wallet connection) - using composable
const { appKit, accountData, isInitializing, openModal } = useAppKit()

const searchShortcut = computed(() => isMac.value ? '⌘K' : 'Ctrl+K')

// Search functionality - redirect to arbitrage page
const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/opportunites', query: { search: searchQuery.value.trim() } })
  } else {
    router.push('/opportunites')
  }
}

// Handle keyboard shortcut (Ctrl+K or Cmd+K)
const handleKeyboardShortcut = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    document.querySelector('.search-input')?.focus()
  }
}

const formatTimeUTC = computed(() => {
  const time = currentTime.value
  const hours = String(time.getUTCHours()).padStart(2, '0')
  const minutes = String(time.getUTCMinutes()).padStart(2, '0')
  const seconds = String(time.getUTCSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds} UTC`
})

// Default fallback data when API fails
const defaultPrices = [
  { symbol: 'BTC', name: 'Bitcoin', price: 100000, change: 2.5 },
  { symbol: 'ETH', name: 'Ethereum', price: 3800, change: 1.8 },
  { symbol: 'USDT', name: 'Tether', price: 1.00, change: 0.01 },
  { symbol: 'XRP', name: 'Ripple', price: 2.35, change: 3.2 },
  { symbol: 'BNB', name: 'BNB', price: 720, change: 1.5 },
  { symbol: 'SOL', name: 'Solana', price: 230, change: 4.2 },
  { symbol: 'USDC', name: 'USD Coin', price: 1.00, change: 0.0 },
  { symbol: 'DOGE', name: 'Dogecoin', price: 0.42, change: -1.2 },
  { symbol: 'ADA', name: 'Cardano', price: 1.05, change: 2.1 },
  { symbol: 'TRX', name: 'Tron', price: 0.26, change: 1.8 },
  { symbol: 'AVAX', name: 'Avalanche', price: 48, change: 3.5 },
  { symbol: 'LINK', name: 'Chainlink', price: 24, change: 2.8 },
  { symbol: 'SHIB', name: 'Shiba Inu', price: 0.000027, change: -0.5 },
  { symbol: 'TON', name: 'Toncoin', price: 6.20, change: 1.9 },
  { symbol: 'DOT', name: 'Polkadot', price: 9.50, change: 2.3 },
]

// Initialize with default data
cryptoPrices.value = [...defaultPrices]
pricesLoading.value = false

const fetchPrices = async () => {
  try {
    const data = await $fetch('/api/crypto-prices')
    
    if (data && data.length > 0) {
      cryptoPrices.value = data.map(item => ({
        symbol: item.symbol,
        name: item.name,
        price: parseFloat(item.price) || 0,
        change: parseFloat(item.change24h) || 0
      }))
    }
    pricesLoading.value = false
  } catch (error) {
    // Keep existing data on error (don't reset to empty)
    console.warn('Backend API unavailable, using cached data:', error.message)
    pricesLoading.value = false
  }
}

const formatPrice = (price) => {
  if (price >= 1000) return '$' + price.toLocaleString('en-US', { maximumFractionDigits: 0 })
  if (price >= 1) return '$' + price.toFixed(2)
  return '$' + price.toFixed(4)
}

const isConnected = computed(() => {
  return Boolean(accountData.value?.isConnected)
})

const formattedAddress = computed(() => {
  if (!accountData.value?.address) return ''
  const addr = String(accountData.value.address)
  return `${addr.slice(0, 6)}...${addr.slice(-4)}`
})

// AppKit loading state (from composable)
const isAppKitLoading = isInitializing

// Open wallet modal using composable
const openWallet = () => {
  openModal()
}

// Watch for wallet connection changes (client-side only)
if (import.meta.client) {
  watch(
    () => accountData.value,
    async (newAccount) => {
      if (newAccount?.isConnected && newAccount?.address) {
        // Extract clean address (remove CAIP prefix like "eip155:1:")
        let cleanAddress = newAccount.address
        if (cleanAddress.includes(':')) {
          cleanAddress = cleanAddress.split(':').pop()
        }
        
        try {
          // Login or register user with wallet
          const response = await $fetch(`/api/users/wallet/${cleanAddress}/login`, {
            method: 'POST'
          })
          setUser(response)
          
          // If user is on admin page but not admin, redirect to home
          if (route.path === '/admin' && response.role !== 'ADMIN') {
            console.warn('🚫 Non-admin on admin page, redirecting...')
            router.push('/')
          }
        } catch (error) {
          console.error('Failed to sync user with backend:', error)
          // Set basic user data even if backend fails
          setUser({ walletAddress: cleanAddress, role: 'USER', plan: 'FREE' })
          
          // Redirect from admin page if not admin
          if (route.path === '/admin') {
            router.push('/')
          }
        }
      } else {
        // Wallet disconnected
        logout()
        
        // Redirect from admin page if disconnected
        if (route.path === '/admin') {
          router.push('/')
        }
      }
    },
    { immediate: true, deep: true }
  )
}

onMounted(() => {
  // OS detection
  isMac.value = navigator.platform.toUpperCase().indexOf('MAC') >= 0 || 
                navigator.userAgent.toUpperCase().indexOf('MAC') >= 0
  
  // Keyboard shortcut
  window.addEventListener('keydown', handleKeyboardShortcut)
  
  // Start clock
  clockInterval = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
  
  // Fetch prices immediately and then every 60 seconds
  fetchPrices()
  priceInterval = setInterval(fetchPrices, 60000)
  
  // Initialize AppKit (done via plugin, get reference here)
  if (typeof window !== 'undefined' && window.__appKit) {
    appKit.value = window.__appKit
    // Subscribe to account changes
    if (appKit.value.subscribeAccount) {
      appKit.value.subscribeAccount((account) => {
        accountData.value = account
      })
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
  if (clockInterval) clearInterval(clockInterval)
  if (priceInterval) clearInterval(priceInterval)
})
</script>

<template>
  <!-- Top Ticker Bar - Only on Overview page -->
  <div v-if="isOverviewPage" class="ticker-bar">
    <div class="ticker-wrapper">
      <div class="ticker-content">
        <!-- Duplicate content for seamless loop -->
        <template v-for="n in 3" :key="n">
          <div 
            v-for="crypto in cryptoPrices" 
            :key="`${n}-${crypto.symbol}`"
            class="ticker-item"
          >
            <span class="ticker-symbol">{{ crypto.symbol }}</span>
            <span :class="['ticker-price', crypto.change >= 0 ? 'positive' : 'negative']">
              {{ formatPrice(crypto.price) }}
            </span>
            <span :class="['ticker-change', crypto.change >= 0 ? 'positive' : 'negative']">
              {{ crypto.change >= 0 ? '▲' : '▼' }} {{ Math.abs(crypto.change).toFixed(1) }}%
            </span>
          </div>
        </template>
      </div>
    </div>
    <!-- Gradient fades for ticker -->
    <div class="ticker-fade-left"></div>
    <div class="ticker-fade-right"></div>
  </div>

  <!-- Header -->
  <header class="header-main">
    <div class="header-left">
      <!-- Logo CactusFund (visible on mobile only) -->
      <NuxtLink to="/" class="mobile-logo">
        <img src="~/assets/logo.png" alt="CactusFund" class="mobile-logo-img" />
        <span class="mobile-logo-text">CactusFund</span>
      </NuxtLink>

      <!-- System Status Badge (hidden on mobile) -->
      <div class="status-badge">
        <div class="status-dot">
          <span class="status-dot-inner"></span>
        </div>
        <span class="status-text">System Operational</span>
      </div>
      
      <!-- Separator (hidden on mobile) -->
      <div class="separator"></div>
      
      <!-- Clock (hidden on mobile) -->
      <div class="clock-section">
        <ClientOnly>
          <span class="iconify clock-icon" data-icon="lucide:clock" data-width="12"></span>
          <span class="clock-time">{{ formatTimeUTC }}</span>
          <template #fallback>
            <span class="clock-time">--:--:-- UTC</span>
          </template>
        </ClientOnly>
      </div>
    </div>

    <div class="header-right">
      <!-- Search - Compact on mobile -->
      <div class="search-box">
        <ClientOnly>
          <span class="iconify search-icon" data-icon="lucide:search" data-width="14" @click="handleSearch" style="cursor: pointer;"></span>
        </ClientOnly>
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search assets..." 
          class="search-input"
          @keyup.enter="handleSearch"
        >
        <span class="search-shortcut">{{ searchShortcut }}</span>
      </div>

      <!-- Connect Wallet Button -->
      <button 
        @click="openWallet" 
        class="wallet-button"
      >
        <!-- Icon only on mobile -->
        <span class="wallet-icon">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/>
          </svg>
          <!-- Connected indicator -->
          <span v-if="isConnected" class="wallet-connected-dot"></span>
        </span>
        <!-- Text on larger screens -->
        <span class="wallet-text">{{ isConnected ? formattedAddress : 'Connect Wallet' }}</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
/* ===== TICKER BAR ===== */
.ticker-bar {
  height: 32px;
  background: #000000;
  border-bottom: 1px solid #27272a;
  position: relative;
  overflow: hidden;
  z-index: 20;
}

.ticker-wrapper {
  height: 100%;
  display: flex;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
}

.ticker-content {
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 0 1rem;
  animation: ticker 30s linear infinite;
  width: max-content;
}

.ticker-content:hover {
  animation-play-state: paused;
}

.ticker-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
}

.ticker-symbol {
  color: #71717a;
  font-weight: 500;
}

.ticker-price {
  font-weight: 500;
}

.ticker-price.positive {
  color: #34d399;
}

.ticker-price.negative {
  color: #f87171;
}

.ticker-change {
  font-size: 12px;
}

.ticker-change.positive {
  color: #059669;
}

.ticker-change.negative {
  color: #dc2626;
}

.ticker-fade-left,
.ticker-fade-right {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 32px;
  pointer-events: none;
  z-index: 10;
}

.ticker-fade-left {
  left: 0;
  background: linear-gradient(to right, #000000, transparent);
}

.ticker-fade-right {
  right: 0;
  background: linear-gradient(to left, #000000, transparent);
}

@keyframes ticker {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-33.33%);
  }
}

/* ===== HEADER ===== */
.header-main {
  height: 56px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.75rem;
  position: sticky;
  top: 0;
  z-index: 30;
}

@media (min-width: 768px) {
  .header-main {
    height: 72px;
    padding: 0 1.5rem;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .header-left {
    gap: 1rem;
  }
}

/* Mobile Logo (visible on mobile only) */
.mobile-logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .mobile-logo {
    display: none;
  }
}

.mobile-logo-img {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.2);
}

.mobile-logo-text {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: -0.02em;
}

/* Status Badge (hidden on mobile) */
.status-badge {
  display: none;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background: rgba(24, 24, 27, 0.5);
  border: 1px solid #27272a;
}

@media (min-width: 768px) {
  .status-badge {
    display: flex;
  }
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse-glow 2s ease-in-out infinite;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .status-dot {
    width: 8px;
    height: 8px;
  }
}

.status-dot-inner {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #10b981;
}

@keyframes pulse-glow {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(16, 185, 129, 0);
  }
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: #34d399;
}

/* Separator */
.separator {
  width: 1px;
  height: 16px;
  background: #27272a;
  display: none;
}

@media (min-width: 768px) {
  .separator {
    display: block;
  }
}

/* Clock */
.clock-section {
  display: none;
  align-items: center;
  gap: 0.5rem;
  color: #71717a;
}

@media (min-width: 768px) {
  .clock-section {
    display: flex;
  }
}

.clock-icon {
  opacity: 0.7;
}

.clock-time {
  font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
}

/* Right Section */
.header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .header-right {
    gap: 0.75rem;
  }
}

/* Search Box */
.search-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  height: 32px;
  background: #18181b;
  border: 1px solid #27272a;
  border-radius: 8px;
  transition: all 0.2s ease;
}

@media (min-width: 768px) {
  .search-box {
    padding: 0.375rem 0.75rem;
    height: 36px;
    border-radius: 10px;
  }
}

.search-box:hover,
.search-box:focus-within {
  border-color: #3f3f46;
}

.search-icon {
  color: #71717a;
  flex-shrink: 0;
}

.search-input {
  background: transparent;
  border: none;
  outline: none;
  font-size: 12px;
  color: #d4d4d8;
  width: 80px;
  min-width: 0;
}

@media (min-width: 640px) {
  .search-input {
    width: 100px;
  }
}

@media (min-width: 768px) {
  .search-input {
    width: 120px;
  }
}

.search-input::placeholder {
  color: #52525b;
}

.search-shortcut {
  font-size: 10px;
  color: #52525b;
  padding: 0.125rem 0.375rem;
  border: 1px solid #3f3f46;
  border-radius: 3px;
  background: rgba(39, 39, 42, 0.5);
  display: none;
}

@media (min-width: 640px) {
  .search-shortcut {
    display: block;
  }
}

/* Wallet Button */
.wallet-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: #ffffff;
  color: #000000;
  padding: 0.375rem;
  height: 32px;
  width: 32px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
  white-space: nowrap;
}

@media (min-width: 640px) {
  .wallet-button {
    padding: 0.375rem 0.75rem;
    width: auto;
  }
}

@media (min-width: 768px) {
  .wallet-button {
    padding: 0.375rem 1rem;
    height: 36px;
    border-radius: 10px;
    font-size: 12px;
  }
}

.wallet-button:hover {
  background: #e4e4e7;
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.15);
}

/* Wallet Icon (visible on mobile) */
.wallet-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (min-width: 640px) {
  .wallet-icon {
    display: none;
  }
}

.wallet-icon svg {
  width: 18px;
  height: 18px;
}

/* Connected indicator dot */
.wallet-connected-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  border: 2px solid #ffffff;
  animation: pulse-glow 2s ease-in-out infinite;
}

/* Wallet Text (hidden on mobile) */
.wallet-text {
  display: none;
}

@media (min-width: 640px) {
  .wallet-text {
    display: inline;
  }
}
</style>
