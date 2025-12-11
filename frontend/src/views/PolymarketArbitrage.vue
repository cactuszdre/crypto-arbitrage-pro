<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { polymarketApi } from '../services/polymarket'
import { useAppKitAccount } from '@reown/appkit/vue'

// Wallet connection
const accountData = useAppKitAccount()
const isConnected = computed(() => {
  if (!accountData || !accountData.value) return false
  return Boolean(accountData.value.isConnected)
})

// Data state
const markets = ref([])
const arbitrageOpportunities = ref([])
const isLoading = ref(true)
const error = ref(null)
const lastUpdate = ref(null)

// View state
const selectedMarket = ref(null)
const viewMode = ref('opportunities') // 'opportunities' | 'all'

// Fetch all 15-min crypto markets
const fetchMarkets = async (silent = false) => {
  try {
    if (!silent) isLoading.value = true
    error.value = null
    
    const response = await polymarketApi.getActiveMarkets()
    markets.value = response.data || []
    lastUpdate.value = new Date()
    
    // Filter for arbitrage opportunities
    arbitrageOpportunities.value = markets.value.filter(m => m.hasArbitrageOpportunity)
    
  } catch (err) {
    console.error('Error fetching markets:', err)
    error.value = err.message
  } finally {
    isLoading.value = false
  }
}

// Format time remaining
const formatTimeRemaining = (seconds) => {
  if (!seconds || seconds <= 0) return 'Expired'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins > 0) return `${mins}m ${secs}s`
  return `${secs}s`
}

// Format price as percentage
const formatPrice = (price) => {
  if (!price) return '0%'
  return (price * 100).toFixed(1) + '¢'
}

// Format profit
const formatProfit = (profit) => {
  if (!profit) return '0%'
  return '+' + (profit * 100).toFixed(2) + '¢'
}

// Get risk level color
const getRiskLevelClass = (seconds) => {
  if (!seconds) return 'text-neutral-500'
  if (seconds < 120) return 'text-red-400' // < 2 min
  if (seconds < 300) return 'text-orange-400' // < 5 min
  if (seconds < 600) return 'text-yellow-400' // < 10 min
  return 'text-emerald-400' // >= 10 min
}

// Select a market for details
const selectMarket = (market) => {
  selectedMarket.value = market
}

// Auto-refresh
let refreshInterval = null
const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshInterval = setInterval(() => {
    fetchMarkets(true) // Silent refresh - no loading animation
  }, 10000) // Every 10 seconds
}

const stopAutoRefresh = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

onMounted(() => {
  fetchMarkets()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})

// Displayed markets based on view mode
const displayedMarkets = computed(() => {
  if (viewMode.value === 'opportunities') {
    return arbitrageOpportunities.value
  }
  return markets.value
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div>
        <h1 class="text-xl sm:text-2xl font-bold text-white flex items-center gap-3">
          <span class="w-10 h-10 rounded-xl bg-gradient-to-br from-purple-500 to-pink-600 flex items-center justify-center">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
            </svg>
          </span>
          Polymarket Arbitrage
        </h1>
        <p class="text-sm text-neutral-500 mt-1">15-minute BTC/ETH prediction markets - Gabagool Strategy</p>
      </div>
      
      <!-- View Toggle -->
      <div class="flex items-center gap-2">
        <button
          @click="viewMode = 'opportunities'"
          :class="[
            'px-4 py-2 text-sm font-medium rounded-lg transition-all',
            viewMode === 'opportunities'
              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
              : 'bg-white/5 text-neutral-400 border border-white/10 hover:bg-white/10'
          ]"
        >
          <span class="flex items-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>
            </svg>
            Opportunities
            <span v-if="arbitrageOpportunities.length > 0" class="px-1.5 py-0.5 text-xs bg-emerald-500 text-white rounded-full">
              {{ arbitrageOpportunities.length }}
            </span>
          </span>
        </button>
        <button
          @click="viewMode = 'all'"
          :class="[
            'px-4 py-2 text-sm font-medium rounded-lg transition-all',
            viewMode === 'all'
              ? 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
              : 'bg-white/5 text-neutral-400 border border-white/10 hover:bg-white/10'
          ]"
        >
          All Markets
        </button>
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="glass-card rounded-xl p-4">
        <p class="text-xs text-neutral-500 mb-1">Active Markets</p>
        <p class="text-xl font-bold text-white">{{ markets.length }}</p>
      </div>
      <div class="glass-card rounded-xl p-4">
        <p class="text-xs text-neutral-500 mb-1">Arbitrage Opportunities</p>
        <p class="text-xl font-bold text-emerald-400">{{ arbitrageOpportunities.length }}</p>
      </div>
      <div class="glass-card rounded-xl p-4">
        <p class="text-xs text-neutral-500 mb-1">Best Profit</p>
        <p class="text-xl font-bold text-emerald-400">
          {{ arbitrageOpportunities.length > 0 ? formatProfit(arbitrageOpportunities[0]?.potentialProfit) : '-' }}
        </p>
      </div>
      <div class="glass-card rounded-xl p-4">
        <p class="text-xs text-neutral-500 mb-1">Last Update</p>
        <p class="text-sm font-medium text-white">
          {{ lastUpdate ? lastUpdate.toLocaleTimeString() : '-' }}
        </p>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Markets List -->
      <div class="lg:col-span-2">
        <div class="glass-card rounded-2xl overflow-hidden">
          <div class="px-6 py-4 border-b border-white/5 flex items-center justify-between">
            <h3 class="text-sm font-medium text-white">
              {{ viewMode === 'opportunities' ? 'Arbitrage Opportunities' : 'All 15-Min Markets' }}
            </h3>
            <span class="text-xs text-neutral-500">
              {{ displayedMarkets.length }} markets
            </span>
          </div>

          <!-- Loading State -->
          <div v-if="isLoading && markets.length === 0" class="p-6">
            <div class="space-y-3">
              <div v-for="i in 5" :key="i" class="flex items-center gap-4 p-4 rounded-xl bg-white/5 animate-pulse">
                <div class="w-10 h-10 rounded-lg bg-white/10"></div>
                <div class="flex-1 space-y-2">
                  <div class="h-3 w-48 bg-white/10 rounded"></div>
                  <div class="h-2 w-32 bg-white/10 rounded"></div>
                </div>
                <div class="h-6 w-16 bg-emerald-500/20 rounded"></div>
              </div>
            </div>
          </div>

          <!-- Error State -->
          <div v-else-if="error" class="p-12 text-center">
            <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-red-500/10 flex items-center justify-center">
              <svg class="w-8 h-8 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
              </svg>
            </div>
            <p class="text-red-400 text-sm mb-2">{{ error }}</p>
            <button @click="fetchMarkets" class="text-xs text-emerald-400 hover:underline">
              Try again
            </button>
          </div>

          <!-- Empty State -->
          <div v-else-if="displayedMarkets.length === 0" class="p-12 text-center">
            <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-white/5 flex items-center justify-center">
              <svg class="w-8 h-8 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"/>
              </svg>
            </div>
            <p class="text-neutral-500 text-sm">
              {{ viewMode === 'opportunities' ? 'No arbitrage opportunities right now' : 'No active 15-min markets found' }}
            </p>
            <p class="text-neutral-600 text-xs mt-1">Markets refresh every 10 seconds</p>
          </div>

          <!-- Markets List -->
          <div v-else class="divide-y divide-white/5">
            <div
              v-for="market in displayedMarkets"
              :key="market.marketId"
              @click="selectMarket(market)"
              :class="[
                'flex items-center gap-4 p-4 cursor-pointer transition-all hover:bg-white/5',
                selectedMarket?.marketId === market.marketId ? 'bg-emerald-500/10 border-l-2 border-emerald-500' : ''
              ]"
            >
              <!-- Market Icon -->
              <div :class="[
                'w-10 h-10 rounded-lg flex items-center justify-center text-sm font-bold',
                market.hasArbitrageOpportunity 
                  ? 'bg-gradient-to-br from-emerald-500 to-teal-600 text-white' 
                  : 'bg-gradient-to-br from-purple-500 to-pink-600 text-white'
              ]">
                {{ market.question?.includes('Bitcoin') ? 'BTC' : market.question?.includes('Ethereum') ? 'ETH' : '?' }}
              </div>

              <!-- Market Info -->
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-white truncate">{{ market.question }}</p>
                <div class="flex items-center gap-3 mt-1">
                  <span class="text-xs text-neutral-500">
                    YES: <span class="text-emerald-400">{{ formatPrice(market.yesPrice) }}</span>
                  </span>
                  <span class="text-xs text-neutral-500">
                    NO: <span class="text-red-400">{{ formatPrice(market.noPrice) }}</span>
                  </span>
                  <span :class="['text-xs', getRiskLevelClass(market.secondsRemaining)]">
                    {{ formatTimeRemaining(market.secondsRemaining) }}
                  </span>
                </div>
              </div>

              <!-- Profit Badge -->
              <div v-if="market.hasArbitrageOpportunity" class="px-3 py-1.5 rounded-lg bg-emerald-500/20 border border-emerald-500/30">
                <span class="text-sm font-bold text-emerald-400">{{ formatProfit(market.potentialProfit) }}</span>
              </div>
              <div v-else class="px-3 py-1.5 rounded-lg bg-white/5">
                <span class="text-sm font-medium text-neutral-400">{{ formatPrice(market.combinedPrice) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Selected Market Details -->
      <div class="lg:col-span-1">
        <div class="glass-card rounded-2xl overflow-hidden sticky top-4">
          <div class="px-6 py-4 border-b border-white/5">
            <h3 class="text-sm font-medium text-white">Market Details</h3>
          </div>

          <div v-if="!selectedMarket" class="p-8 text-center">
            <div class="w-12 h-12 mx-auto mb-3 rounded-full bg-white/5 flex items-center justify-center">
              <svg class="w-6 h-6 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5M7.188 2.239l.777 2.897M5.136 7.965l-2.898-.777M13.95 4.05l-2.122 2.122m-5.657 5.656l-2.12 2.122"/>
              </svg>
            </div>
            <p class="text-neutral-500 text-sm">Select a market to view details</p>
          </div>

          <div v-else class="p-6 space-y-4">
            <!-- Market Question -->
            <div>
              <p class="text-xs text-neutral-500 mb-1">Question</p>
              <p class="text-sm text-white">{{ selectedMarket.question }}</p>
            </div>

            <!-- Prices -->
            <div class="grid grid-cols-2 gap-3">
              <div class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                <p class="text-xs text-emerald-400 mb-1">YES Price</p>
                <p class="text-lg font-bold text-white">{{ formatPrice(selectedMarket.yesPrice) }}</p>
              </div>
              <div class="p-3 rounded-xl bg-red-500/10 border border-red-500/20">
                <p class="text-xs text-red-400 mb-1">NO Price</p>
                <p class="text-lg font-bold text-white">{{ formatPrice(selectedMarket.noPrice) }}</p>
              </div>
            </div>

            <!-- Combined Price -->
            <div class="p-3 rounded-xl bg-white/5 border border-white/10">
              <div class="flex items-center justify-between">
                <p class="text-xs text-neutral-400">Combined Price</p>
                <p :class="[
                  'text-lg font-bold',
                  selectedMarket.combinedPrice < 1 ? 'text-emerald-400' : 'text-neutral-300'
                ]">
                  {{ formatPrice(selectedMarket.combinedPrice) }}
                </p>
              </div>
              <p v-if="selectedMarket.hasArbitrageOpportunity" class="text-xs text-emerald-400 mt-2">
                ✓ Arbitrage opportunity: {{ formatProfit(selectedMarket.potentialProfit) }} per share pair
              </p>
            </div>

            <!-- Time Remaining -->
            <div class="p-3 rounded-xl bg-white/5 border border-white/10">
              <div class="flex items-center justify-between">
                <p class="text-xs text-neutral-400">Time Remaining</p>
                <p :class="['text-lg font-bold', getRiskLevelClass(selectedMarket.secondsRemaining)]">
                  {{ formatTimeRemaining(selectedMarket.secondsRemaining) }}
                </p>
              </div>
            </div>

            <!-- Token IDs -->
            <div class="space-y-2">
              <p class="text-xs text-neutral-500">Token IDs (for SDK)</p>
              <div class="p-2 rounded-lg bg-neutral-800 border border-white/5">
                <p class="text-[10px] text-neutral-400 mb-1">YES Token:</p>
                <p class="text-[10px] text-white font-mono break-all">{{ selectedMarket.tokenIdYes || 'N/A' }}</p>
              </div>
              <div class="p-2 rounded-lg bg-neutral-800 border border-white/5">
                <p class="text-[10px] text-neutral-400 mb-1">NO Token:</p>
                <p class="text-[10px] text-white font-mono break-all">{{ selectedMarket.tokenIdNo || 'N/A' }}</p>
              </div>
            </div>

            <!-- Trade Button (Coming Soon) -->
            <div class="pt-4 border-t border-white/5">
              <button
                disabled
                class="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-purple-500/50 to-pink-500/50 text-white font-medium text-sm opacity-50 cursor-not-allowed"
              >
                <span class="flex items-center justify-center gap-2">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                  </svg>
                  Trade (Coming Soon)
                </span>
              </button>
              <p class="text-[10px] text-neutral-500 text-center mt-2">
                Phase 3 will enable trading via your connected wallet
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Strategy Info -->
    <div class="glass-card rounded-2xl p-6">
      <h3 class="text-sm font-medium text-white mb-4 flex items-center gap-2">
        <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        Gabagool Strategy Explained
      </h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
        <div class="p-4 rounded-xl bg-white/5">
          <p class="text-emerald-400 font-medium mb-2">1. Find Opportunities</p>
          <p class="text-neutral-400 text-xs">Look for markets where YES + NO combined price &lt; $1.00</p>
        </div>
        <div class="p-4 rounded-xl bg-white/5">
          <p class="text-purple-400 font-medium mb-2">2. Buy Both Sides</p>
          <p class="text-neutral-400 text-xs">Buy YES when cheap, then buy NO when it dips. Keep positions balanced.</p>
        </div>
        <div class="p-4 rounded-xl bg-white/5">
          <p class="text-pink-400 font-medium mb-2">3. Guaranteed Profit</p>
          <p class="text-neutral-400 text-xs">At expiry, winning side = $1.00. Combined cost &lt; $1.00 = profit.</p>
        </div>
      </div>
    </div>
  </div>
</template>
