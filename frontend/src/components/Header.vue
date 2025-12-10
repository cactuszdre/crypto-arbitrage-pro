<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppKit, useAppKitAccount } from '@reown/appkit/vue'
import { useAuth } from '../stores/auth'
import { userApi, cryptoPriceApi } from '../services/api'

const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const { open } = useAppKit()
const accountData = useAppKitAccount()
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
onMounted(() => {
  isMac.value = navigator.platform.toUpperCase().indexOf('MAC') >= 0 || 
                navigator.userAgent.toUpperCase().indexOf('MAC') >= 0
})

const searchShortcut = computed(() => isMac.value ? '⌘K' : 'Ctrl+K')

// Search functionality - redirect to arbitrage page
const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/arbitrage', query: { search: searchQuery.value.trim() } })
  } else {
    router.push('/arbitrage')
  }
}

// Handle keyboard shortcut (Ctrl+K or Cmd+K)
const handleKeyboardShortcut = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    document.querySelector('.search-input')?.focus()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyboardShortcut)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
})

const formatTime = computed(() => {
  const time = currentTime.value
  return time.toLocaleTimeString('fr-FR', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit' 
  })
})

const formatDate = computed(() => {
  const date = currentTime.value
  return date.toLocaleDateString('fr-FR', { 
    weekday: 'short',
    day: 'numeric', 
    month: 'short'
  })
})

const formatTimeUTC = computed(() => {
  const time = currentTime.value
  const hours = String(time.getUTCHours()).padStart(2, '0')
  const minutes = String(time.getUTCMinutes()).padStart(2, '0')
  const seconds = String(time.getUTCSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds} UTC`
})

// Fetch crypto prices from CoinGecko - Top 15 by market cap
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
    const response = await cryptoPriceApi.getAll()
    
    if (response.data && response.data.length > 0) {
      cryptoPrices.value = response.data.map(item => ({
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

const formatChange = (change) => {
  const sign = change >= 0 ? '+' : ''
  return sign + change.toFixed(2) + '%'
}

// Watch for wallet connection changes and sync with our auth store
watch(
  () => accountData.value,
  async (newAccount) => {
    console.log('🔄 Account changed:', newAccount)
    
    if (newAccount?.isConnected && newAccount?.address) {
      // Extract clean address (remove CAIP prefix like "eip155:1:")
      let cleanAddress = newAccount.address
      if (cleanAddress.includes(':')) {
        cleanAddress = cleanAddress.split(':').pop()
      }
      
      console.log('🔑 Clean wallet address:', cleanAddress)
      
      try {
        // Login or register user with wallet, then get their role from backend
        const response = await userApi.updateLastLogin(cleanAddress)
        setUser(response.data)
        console.log('✅ User synced with backend:', response.data)
        
        // If user is on admin page but not admin, redirect to home
        if (route.path === '/admin' && response.data.role !== 'ADMIN') {
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
      console.log('👋 User logged out')
      
      // Redirect from admin page if disconnected
      if (route.path === '/admin') {
        router.push('/')
      }
    }
  },
  { immediate: true, deep: true }
)

const isConnected = computed(() => {
  if (!accountData || !accountData.value) return false
  return Boolean(accountData.value.isConnected)
})

const formattedAddress = computed(() => {
  if (!accountData || !accountData.value || !accountData.value.address) return ''
  const addr = String(accountData.value.address)
  return `${addr.slice(0, 6)}...${addr.slice(-4)}`
})

onMounted(() => {
  // Start clock
  clockInterval = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
  
  // Fetch prices immediately and then every 60 seconds (respect CoinGecko rate limits)
  fetchPrices()
  priceInterval = setInterval(fetchPrices, 60000)
})

onUnmounted(() => {
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
      <!-- System Status Badge -->
      <div class="status-badge">
        <div class="status-dot">
          <span class="status-dot-inner"></span>
        </div>
        <span class="status-text">System Operational</span>
      </div>
      
      <!-- Separator -->
      <div class="separator"></div>
      
      <!-- Clock -->
      <div class="clock-section">
        <span class="iconify clock-icon" data-icon="lucide:clock" data-width="12"></span>
        <span class="clock-time">{{ formatTimeUTC }}</span>
      </div>
    </div>

    <div class="header-right">
      <!-- Search -->
      <div class="search-box">
        <span class="iconify search-icon" data-icon="lucide:search" data-width="14" @click="handleSearch" style="cursor: pointer;"></span>
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search assets..." 
          class="search-input"
          @keyup.enter="handleSearch"
        >
        <span class="search-shortcut" @click="handleSearch" style="cursor: pointer;">{{ searchShortcut }}</span>
      </div>

      <!-- Connect Wallet Button -->
      <button 
        @click="open()" 
        class="wallet-button"
      >
        {{ isConnected ? formattedAddress : 'Connect Wallet' }}
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
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.ticker-price {
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.ticker-price.positive {
  color: #34d399;
}

.ticker-price.negative {
  color: #f87171;
}

.ticker-change {
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  line-height: 1;
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
  height: 72px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  position: sticky;
  top: 0;
  z-index: 30;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

/* Status Badge */
.status-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background: rgba(24, 24, 27, 0.5);
  border: 1px solid #27272a;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse-glow 2s ease-in-out infinite;
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
}

/* Clock */
.clock-section {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #71717a;
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
  gap: 0.75rem;
}

/* Search Box */
.search-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  height: 36px;
  background: #18181b;
  border: 1px solid #27272a;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.search-box:hover,
.search-box:focus-within {
  border-color: #3f3f46;
}

.search-icon {
  color: #71717a;
}

.search-input {
  background: transparent;
  border: none;
  outline: none;
  font-size: 12px;
  color: #d4d4d8;
  width: 120px;
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
}

/* Wallet Button */
.wallet-button {
  background: #ffffff;
  color: #000000;
  padding: 0.375rem 1rem;
  height: 36px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
}

.wallet-button:hover {
  background: #e4e4e7;
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.15);
}
</style>

