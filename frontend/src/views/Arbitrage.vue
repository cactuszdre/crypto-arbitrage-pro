<script setup>
import { ref, onMounted, onUnmounted, computed, watch, unref } from 'vue'
import { useRoute } from 'vue-router'
import { arbitrageApi, dexApi } from '../services/api'
import { useAppKitAccount, useAppKitProvider } from '@reown/appkit/vue'
import { executeHyperliquidOrder } from '../services/hyperliquid'
import { executeParadexOrder } from '../services/paradex'
import { executeLighterOrder } from '../services/lighter'
import { useAuth } from '../stores/auth'

// Route for reading query params
const route = useRoute()

// Auth store for Pro plan check
const { state: authState } = useAuth()
const isPro = computed(() => authState.user?.plan === 'PRO' || authState.user?.role === 'ADMIN')

const opportunities = ref([])
const rawRates = ref([]) // Raw rates by symbol for dynamic recalculation
const currentPage = ref(1)
const itemsPerPage = 8
const failedImages = ref(new Set())
const searchQuery = ref('')
const isLoadingOpportunities = ref(true)

// Pro Plan Modal
const showProModal = ref(false)

// Wallet & Execution State
const accountData = useAppKitAccount()
const providerData = useAppKitProvider('eip155')

const provider = computed(() => {
  if (providerData && providerData.walletProvider) {
      return providerData.walletProvider.value || providerData.walletProvider
  }
  return null
})

const selectedOpportunity = ref(null)
// executionSide is no longer main driver, we execute BOTH.
const tradeSize = ref(100) // Default 100 USD (per leg)
const isExecuting = ref(false)

// Logs for Dual Execution
const executionLogA = ref(null) // Long Leg
const executionLogB = ref(null) // Short Leg

const isConnected = computed(() => {
  if (!accountData || !accountData.value) return false
  return Boolean(accountData.value.isConnected)
})

const selectOpportunity = (opp) => {
  selectedOpportunity.value = opp
  executionLogA.value = null
  executionLogB.value = null
}

// Router to correct service
const executeDexOrder = async (dexName, side, size, symbol) => {
    const details = { symbol, isBuy: side === 'Buy', size, price: "0" }

    switch(dexName.toLowerCase()) {
        case 'hyperliquid':
            return await executeHyperliquidOrder(provider.value, details)
        case 'paradex':
            return await executeParadexOrder(provider.value, details)
        case 'lighter':
             return await executeLighterOrder(provider.value, details)
        default:
            throw new Error(`DEX ${dexName} execution not supported`)
    }
}

const executeTrade = async () => {
  if (!isConnected.value || !selectedOpportunity.value) return

  isExecuting.value = true
  executionLogA.value = { status: 'pending', message: 'Executing...' }
  executionLogB.value = { status: 'pending', message: 'Executing...' }

  try {
    const symbol = getBaseSymbol(selectedOpportunity.value.symbol).toUpperCase()
    if (!provider.value) throw new Error("Wallet not connected")

    const opp = selectedOpportunity.value
    const size = tradeSize.value.toString()

    // Execute in Parallel
    const promiseA = executeDexOrder(opp.longDex, 'Buy', size, symbol)
        .then(res => { executionLogA.value = { success: true, message: res.message } })
        .catch(err => { executionLogA.value = { success: false, message: err.message } })

    const promiseB = executeDexOrder(opp.shortDex, 'Sell', size, symbol)
        .then(res => { executionLogB.value = { success: true, message: res.message } })
        .catch(err => { executionLogB.value = { success: false, message: err.message } })

    await Promise.all([promiseA, promiseB])

  } catch (error) {
    console.error('Execution init failed:', error)
  } finally {
    isExecuting.value = false
  }
}

// Sorting state
const sortColumn = ref('projectedDailyReturn') // default sort by daily return
const sortDirection = ref('desc') // 'asc' or 'desc'
// Sorting state


// Time period filter
const selectedPeriod = ref('current') // 'current', '1d', '7d', '30d', '3m', '6m', '1y'
const periodOptions = [
  { value: 'current', label: 'Live', days: 0 },
  { value: '1d', label: '1 Day', days: 1 },
  { value: '7d', label: '7 Days', days: 7 },
  { value: '30d', label: '30 Days', days: 30 },
  { value: '3m', label: '3 Months', days: 90 },
  { value: '6m', label: '6 Months', days: 180 },
  { value: '1y', label: '1 Year', days: 365 }
]
const isLoadingHistorical = ref(false)
const isPreloadingHistorical = ref(false) // True during initial preload phase

// Historical data metadata (period info from backend)
const historicalMeta = ref(null) // { periodLabel, actualStartDate, actualEndDate, dataPointCount }

// Cache for preloaded historical data (keyed by days)
const historicalDataCache = new Map()

// DEX filter - all DEXes selected by default
const selectedDexes = ref([]) // Will be initialized with all DEXes on mount
const showDexFilter = ref(false)

// Available DEXes loaded from database (loaded once at mount)
const availableDexes = ref([])

// Load DEX list from database (called once at mount)
const loadAvailableDexes = async () => {
  try {
    const response = await dexApi.getAll()
    // Extract DEX names from response
    availableDexes.value = response.data.map(dex => dex.name).sort()
    // Initialize selection with all DEXes
    selectedDexes.value = [...availableDexes.value]
  } catch (error) {
    console.error('Failed to load DEX list:', error)
    // Fallback to known DEXes if API fails
    availableDexes.value = ['Hyperliquid', 'Paradex', 'Lighter']
    selectedDexes.value = [...availableDexes.value]
  }
}

// Toggle DEX selection - simple add/remove
const toggleDex = (dex) => {
  const index = selectedDexes.value.indexOf(dex)
  if (index === -1) {
    selectedDexes.value.push(dex)
  } else {
    selectedDexes.value.splice(index, 1)
  }
}

// Check if DEX is selected
const isDexSelected = (dex) => {
  return selectedDexes.value.includes(dex)
}

// Check if we have enough DEXes selected (need at least 2 for arbitrage)
const hasEnoughDexes = computed(() => {
  return selectedDexes.value.length >= 2
})

// Select all DEXes
const selectAllDexes = () => {
  selectedDexes.value = [...availableDexes.value]
}

// Deselect all DEXes
const deselectAllDexes = () => {
  selectedDexes.value = []
}

// Toggle sort on column click
const toggleSort = (column) => {
  if (sortColumn.value === column) {
    // Toggle direction if same column
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    // New column, default to desc for numeric values
    sortColumn.value = column
    sortDirection.value = 'desc'
  }
}

// Get sort state for column (returns 'neutral', 'asc', or 'desc')
const getSortState = (column) => {
  if (sortColumn.value !== column) return 'neutral'
  return sortDirection.value
}

// Calculate best arbitrage routes from raw rates based on selected DEXes
const calculateOpportunitiesFromRates = (rates, selectedDexList) => {
  if (!rates || rates.length === 0 || selectedDexList.length < 2) return []
  
  return rates.map(item => {
    // Filter to only selected DEXes
    const filteredRates = item.rates.filter(r => selectedDexList.includes(r.dex))
    
    if (filteredRates.length < 2) return null
    
    // Find best long (lowest rate) and best short (highest rate)
    const bestLong = filteredRates.reduce((a, b) => 
      parseFloat(a.rateAnnual) < parseFloat(b.rateAnnual) ? a : b
    )
    const bestShort = filteredRates.reduce((a, b) => 
      parseFloat(a.rateAnnual) > parseFloat(b.rateAnnual) ? a : b
    )
    
    // Skip if same DEX
    if (bestLong.dex === bestShort.dex) return null
    
    const longRate = parseFloat(bestLong.rateAnnual)
    const shortRate = parseFloat(bestShort.rateAnnual)
    const spread = shortRate - longRate
    
    // Only positive spreads
    if (spread <= 0) return null
    
    return {
      symbol: item.symbol,
      longDex: bestLong.dex,
      shortDex: bestShort.dex,
      longRate: longRate,
      shortRate: shortRate,
      spread: spread,
      projectedDailyReturn: spread / 365
    }
  }).filter(Boolean)
}

// Computed opportunities - recalculates when DEX selection or raw rates change
const computedOpportunities = computed(() => {
  if (rawRates.value.length === 0) {
    // Fall back to pre-calculated opportunities if no raw rates
    return opportunities.value
  }
  return calculateOpportunitiesFromRates(rawRates.value, selectedDexes.value)
})

// Filter opportunities based on search query
const filteredOpportunities = computed(() => {
  // If less than 2 DEXes selected, return empty (need at least 2 for arbitrage)
  if (selectedDexes.value.length < 2) {
    return []
  }

  let result = computedOpportunities.value

  // Apply search filter
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase().trim()
    result = result.filter(opp =>
      opp.symbol.toLowerCase().includes(query) ||
      getBaseSymbol(opp.symbol).includes(query) ||
      opp.longDex?.toLowerCase().includes(query) ||
      opp.shortDex?.toLowerCase().includes(query)
    )
  }

  return result
})

// Sort opportunities
const sortedOpportunities = computed(() => {
  const sorted = [...filteredOpportunities.value]

  sorted.sort((a, b) => {
    let valA, valB

    switch (sortColumn.value) {
      case 'projectedDailyReturn':
        valA = a.projectedDailyReturn || 0
        valB = b.projectedDailyReturn || 0
        break
      case 'longRate':
        valA = a.longRate || 0
        valB = b.longRate || 0
        break
      case 'shortRate':
        valA = a.shortRate || 0
        valB = b.shortRate || 0
        break
      case 'symbol':
        valA = a.symbol || ''
        valB = b.symbol || ''
        return sortDirection.value === 'asc'
          ? valA.localeCompare(valB)
          : valB.localeCompare(valA)
      default:
        valA = a.projectedDailyReturn || 0
        valB = b.projectedDailyReturn || 0
    }

    if (sortDirection.value === 'asc') {
      return valA - valB
    } else {
      return valB - valA
    }
  })

  return sorted
})

const totalPages = computed(() => Math.ceil(sortedOpportunities.value.length / itemsPerPage))

const paginatedOpportunities = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage
  const end = start + itemsPerPage
  return sortedOpportunities.value.slice(start, end)
})

// Reset to first page when search or sort changes
const handleSearch = () => {
  currentPage.value = 1
}

watch([sortColumn, sortDirection], () => {
  currentPage.value = 1
})

const prevPage = () => {
  if (currentPage.value > 1) currentPage.value--
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) currentPage.value++
}

// Extract base symbol from trading pair (e.g., "BTC-USD-PERP" -> "btc")
const getBaseSymbol = (symbol) => {
  if (!symbol) return ''
  // Handle formats like "BTC-USD-PERP", "ETH-USDT", "BTCUSD", etc.
  const cleaned = symbol.toUpperCase()
    .replace(/-USD-PERP$/i, '')
    .replace(/-PERP$/i, '')
    .replace(/-USD$/i, '')
    .replace(/-USDT$/i, '')
    .replace(/USDT$/i, '')
    .replace(/USD$/i, '')
  return cleaned.toLowerCase()
}

// Get crypto icon URL with multiple fallback sources
// Get crypto icon URL with multiple fallback sources
const getCryptoIcon = (symbol) => {
  const base = getBaseSymbol(symbol).toUpperCase()
  return `https://app.paradex.trade/static/market/${base}.svg`
}

// Fallback icon using crypto-icons (GitHub hosted, very reliable)
const getFallbackIcon = (symbol) => {
  const base = getBaseSymbol(symbol).toUpperCase()
  return `https://app.paradex.trade/static/market/${base}.svg`
}

// Handle image load error - mark as failed
const handleImageError = (symbol, event) => {
  failedImages.value.add(symbol)
  event.target.style.display = 'none'
}

// Check if image failed to load
const isImageFailed = (symbol) => {
  return failedImages.value.has(symbol)
}

// Format rate with color based on sign
const formatRate = (rate) => {
  if (rate === null || rate === undefined) return { value: 'N/A', class: 'text-neutral-500' }
  const formatted = rate.toFixed(2) + '%'
  if (rate > 0) return { value: '+' + formatted, class: 'text-emerald-400' }
  if (rate < 0) return { value: formatted, class: 'text-red-400' }
  return { value: formatted, class: 'text-neutral-400' }
}

// Fetch opportunities - now uses raw rates for dynamic recalculation
const fetchOpportunities = async (silent = false) => {
  if (!silent) isLoadingOpportunities.value = true
  try {
    // Fetch raw rates for frontend-side calculation
    const response = await arbitrageApi.getAllRates()
    rawRates.value = response.data
    // Clear pre-calculated opportunities since we're using rawRates now
    opportunities.value = []
  } catch (error) {
    console.error('Failed to fetch raw rates:', error)
    // Fallback to old endpoint if new one fails
    try {
      const fallbackResponse = await arbitrageApi.getOpportunities()
      opportunities.value = fallbackResponse.data
      rawRates.value = []
    } catch (fallbackError) {
      console.error('Failed to fetch opportunities:', fallbackError)
      opportunities.value = []
      rawRates.value = []
    }
  } finally {
    isLoadingOpportunities.value = false
  }
}

// Fetch and cache historical data for a given period
const fetchAndCacheHistoricalData = async (days) => {
  try {
    const response = await arbitrageApi.getHistoricalRates(days)
    const data = response.data
    
    // Store in cache
    historicalDataCache.set(days, {
      rawRates: data.symbols || [],
      meta: {
        periodLabel: data.periodLabel,
        actualStartDate: data.actualStartDate,
        actualEndDate: data.actualEndDate,
        dataPointCount: data.dataPointCount,
        requestedDays: data.requestedDays
      }
    })
    return true
  } catch (error) {
    console.error(`Failed to fetch historical data for ${days} days:`, error)
    return false
  }
}

// Preload all historical periods in parallel at page mount
const preloadAllHistoricalData = async () => {
  isPreloadingHistorical.value = true
  const periodsToLoad = periodOptions.filter(p => p.days > 0).map(p => p.days)
  
  // Fetch all periods in parallel
  await Promise.all(periodsToLoad.map(days => fetchAndCacheHistoricalData(days)))
  
  isPreloadingHistorical.value = false
  console.log('Historical data preloaded for periods:', periodsToLoad)
}

// Apply cached historical data to current view (instant, no fetch)
const applyHistoricalDataFromCache = (days) => {
  const cached = historicalDataCache.get(days)
  if (cached) {
    rawRates.value = cached.rawRates
    opportunities.value = []
    historicalMeta.value = cached.meta
    return true
  }
  return false
}

// Fetch historical opportunities - now uses cache first
const fetchHistoricalOpportunities = async (days) => {
  // Try cache first (instant)
  if (applyHistoricalDataFromCache(days)) {
    return
  }
  
  // If not in cache (shouldn't happen after preload), fetch it
  isLoadingHistorical.value = true
  historicalMeta.value = null
  
  const success = await fetchAndCacheHistoricalData(days)
  if (success) {
    applyHistoricalDataFromCache(days)
  } else {
    // Fallback to old endpoint
    try {
      const fallbackResponse = await arbitrageApi.getHistoricalOpportunities(days)
      const fallbackData = fallbackResponse.data
      opportunities.value = fallbackData.opportunities || []
      rawRates.value = []
      historicalMeta.value = {
        periodLabel: fallbackData.periodLabel,
        actualStartDate: fallbackData.actualStartDate,
        actualEndDate: fallbackData.actualEndDate,
        dataPointCount: fallbackData.dataPointCount,
        requestedDays: fallbackData.requestedDays
      }
    } catch (fallbackError) {
      console.error('Failed to fetch historical opportunities:', fallbackError)
      opportunities.value = []
      rawRates.value = []
      historicalMeta.value = null
    }
  }
  
  isLoadingHistorical.value = false
}

// Watch period changes
watch(selectedPeriod, (newPeriod) => {
  currentPage.value = 1
  const option = periodOptions.find(o => o.value === newPeriod)
  if (option && option.days > 0) {
    fetchHistoricalOpportunities(option.days)
  } else {
    // Reset historical meta when switching back to Live mode
    historicalMeta.value = null
    fetchOpportunities()
  }
})

// Generate DEX URL
const getDexUrl = (dexName, symbol) => {
  const base = getBaseSymbol(symbol)

  switch(dexName.toLowerCase()) {
    case 'paradex':
      return `https://app.paradex.trade/trade/${symbol}`
    case 'hyperliquid':
      return `https://app.hyperliquid.xyz/trade/${base}`
    case 'lighter':
      return `https://app.lighter.xyz/trade/${base.toUpperCase()}`
    case 'dydx':
      return `https://dydx.exchange/trade/${base}-USD`
    default:
      return '#'
  }
}

// Auto-refresh every 10 seconds (only in Live mode)
let refreshInterval = null

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshInterval = setInterval(() => {
    if (selectedPeriod.value === 'current') {
      fetchOpportunities(true) // Silent refresh
    }
  }, 10000)
}

const stopAutoRefresh = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

onMounted(async () => {
  // Load DEX list from database first
  await loadAvailableDexes()
  // Initialize search from URL query param
  if (route.query.search) {
    searchQuery.value = route.query.search
  }
  
  // Fetch live data first (show immediately)
  fetchOpportunities()
  startAutoRefresh()
  
  // Preload all historical data in background (for instant filter switching)
  preloadAllHistoricalData()
})

// Watch for route query changes (when search is updated from Header)
watch(() => route.query.search, (newSearch) => {
  if (newSearch !== undefined) {
    searchQuery.value = newSearch || ''
    handleSearch()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">

    <!-- Opportunities List -->
    <div class="lg:col-span-2 space-y-4 sm:space-y-6">
      <div class="glass-card rounded-2xl sm:rounded-3xl overflow-visible">
        <div class="px-4 sm:px-6 py-4 sm:py-5 border-b border-white/5 overflow-visible">
          <div class="flex items-center justify-between mb-3 sm:mb-4">
            <h3 class="text-sm sm:text-base font-medium text-white">Live Opportunities</h3>
            <span class="text-[10px] sm:text-xs text-emerald-400">
              {{ filteredOpportunities.length }}
              <span v-if="searchQuery.trim()">/ {{ opportunities.length }}</span> found
            </span>
          </div>

          <!-- Search Bar -->
          <div class="relative">
            <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <svg class="w-4 h-4 text-neutral-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
              </svg>
            </div>
            <input
              v-model="searchQuery"
              @input="handleSearch"
              type="text"
              placeholder="Search by ticker or DEX..."
              class="w-full pl-10 pr-10 py-2 sm:py-2.5 bg-white/5 border border-white/10 rounded-lg sm:rounded-xl text-xs sm:text-sm text-white placeholder-neutral-500 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/50 transition-all"
            >
            <!-- Clear button -->
            <button
              v-if="searchQuery"
              @click="searchQuery = ''; handleSearch()"
              class="absolute inset-y-0 right-0 pr-3 flex items-center text-neutral-500 hover:text-white transition-colors"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>

          <!-- Period Filter -->
          <div class="flex flex-wrap items-center gap-1.5 sm:gap-2 mt-3 sm:mt-4">
            <span class="text-[10px] sm:text-xs text-neutral-500 mr-1 sm:mr-2">Period:</span>
            <button
              v-for="option in periodOptions"
              :key="option.value"
              @click="selectedPeriod = option.value"
              :class="[
                'px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium rounded-md sm:rounded-lg transition-all',
                selectedPeriod === option.value
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                  : 'bg-white/5 text-neutral-400 border border-white/10 hover:bg-white/10 hover:text-white'
              ]"
            >
              {{ option.label }}
            </button>
            <span v-if="selectedPeriod !== 'current' && isLoadingHistorical" class="text-[10px] text-emerald-400 ml-2">
              <svg class="w-3 h-3 inline animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Loading...
            </span>

            <!-- DEX Filter Dropdown -->
            <div class="relative ml-auto">
              <button
                @click="showDexFilter = !showDexFilter"
                :class="[
                  'flex items-center gap-1 sm:gap-2 px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium rounded-md sm:rounded-lg border transition-all',
                  !hasEnoughDexes
                    ? 'bg-red-500/10 text-red-400 border-red-500/30'
                    : 'bg-white/5 text-neutral-400 border-white/10 hover:bg-white/10 hover:text-white'
                ]"
              >
                <svg class="w-3 h-3 sm:w-4 sm:h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z"></path>
                </svg>
                <span class="hidden sm:inline">DEX Filter</span>
                <span
                  v-if="selectedDexes.length !== availableDexes.length"
                  :class="[
                    'text-[10px] px-1.5 py-0.5 rounded-full',
                    hasEnoughDexes ? 'bg-emerald-500 text-white' : 'bg-red-500 text-white'
                  ]"
                >
                  {{ selectedDexes.length }}/{{ availableDexes.length }}
                </span>
                <svg class="w-3 h-3 transition-transform" :class="{ 'rotate-180': showDexFilter }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                </svg>
              </button>

              <!-- Dropdown Panel -->
              <div
                v-if="showDexFilter"
                class="absolute right-0 top-full mt-2 w-56 bg-neutral-900 border border-white/10 rounded-xl shadow-xl z-50 overflow-hidden"
              >
                <div class="p-2 border-b border-white/5 flex gap-2">
                  <button
                    @click="selectAllDexes"
                    class="flex-1 px-3 py-2 text-xs text-neutral-400 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
                  >
                    Select All
                  </button>
                  <button
                    @click="deselectAllDexes"
                    class="flex-1 px-3 py-2 text-xs text-neutral-400 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
                  >
                    Deselect All
                  </button>
                </div>

                <!-- Warning message -->
                <div v-if="!hasEnoughDexes" class="p-2 bg-red-500/10 border-b border-red-500/20">
                  <p class="text-[10px] text-red-400 text-center">
                    <svg class="w-3 h-3 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                    </svg>
                    Select at least 2 DEXes
                  </p>
                </div>

                <div class="p-2 max-h-48 overflow-y-auto">
                  <label
                    v-for="dex in availableDexes"
                    :key="dex"
                    class="flex items-center gap-3 px-3 py-2 text-xs hover:bg-white/5 rounded-lg cursor-pointer transition-colors"
                  >
                    <input
                      type="checkbox"
                      :checked="isDexSelected(dex)"
                      @change="toggleDex(dex)"
                      class="w-4 h-4 rounded border-emerald-500/50 bg-emerald-500/10 text-emerald-500 focus:ring-emerald-500/50 accent-emerald-500 cursor-pointer"
                    >
                    <span :class="isDexSelected(dex) ? 'text-white' : 'text-neutral-500'">{{ dex }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>

          <!-- Historical Data Info Banner -->
          <div 
            v-if="selectedPeriod !== 'current' && !isLoadingHistorical && historicalMeta && historicalMeta.dataPointCount > 0"
            class="mt-3 sm:mt-4 p-3 rounded-xl bg-gradient-to-r from-emerald-500/10 to-cyan-500/10 border border-emerald-500/20"
          >
            <div class="flex items-center gap-2">
              <svg class="w-4 h-4 text-emerald-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path>
              </svg>
              <div class="flex-1 min-w-0">
                <p class="text-xs sm:text-sm text-white font-medium truncate">
                  {{ historicalMeta.periodLabel }}
                </p>
                <p class="text-[10px] sm:text-xs text-neutral-400">
                  Historical average based on {{ historicalMeta.dataPointCount }} day{{ historicalMeta.dataPointCount > 1 ? 's' : '' }} of data
                </p>
              </div>
            </div>
          </div>

          <!-- No Historical Data Warning -->
          <div 
            v-else-if="selectedPeriod !== 'current' && !isLoadingHistorical && (!historicalMeta || historicalMeta.dataPointCount === 0)"
            class="mt-3 sm:mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20"
          >
            <div class="flex items-center gap-2">
              <svg class="w-4 h-4 text-amber-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
              </svg>
              <p class="text-xs text-amber-400">
                No historical data available for this period yet
              </p>
            </div>
          </div>
        </div>

        <!-- Skeleton Loading -->
        <div v-if="isLoadingOpportunities && opportunities.length === 0" class="p-3 sm:p-4">
          <div class="space-y-2 sm:space-y-3">
            <div v-for="i in 8" :key="i" class="flex items-center gap-3 sm:gap-4 p-3 sm:p-4 rounded-lg sm:rounded-xl bg-white/5 animate-pulse">
              <div class="w-6 h-6 sm:w-8 sm:h-8 rounded-full bg-white/10"></div>
              <div class="flex-1 space-y-1.5 sm:space-y-2">
                <div class="h-2.5 sm:h-3 w-20 sm:w-24 bg-white/10 rounded"></div>
                <div class="h-2 w-24 sm:w-32 bg-white/10 rounded"></div>
              </div>
              <div class="h-2.5 sm:h-3 w-12 sm:w-16 bg-white/10 rounded"></div>
              <div class="h-2.5 sm:h-3 w-12 sm:w-16 bg-white/10 rounded"></div>
              <div class="h-2.5 sm:h-3 w-16 sm:w-20 bg-emerald-500/20 rounded"></div>
            </div>
          </div>
        </div>

        <!-- Empty State: No data at all -->
        <div v-else-if="rawRates.length === 0 && opportunities.length === 0" class="p-8 sm:p-12 text-center">
          <div class="w-12 h-12 sm:w-16 sm:h-16 mx-auto mb-3 sm:mb-4 rounded-full bg-white/5 flex items-center justify-center">
            <svg class="w-6 h-6 sm:w-8 sm:h-8 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-xs sm:text-sm">Not enough data yet</p>
          <p class="text-neutral-600 text-[10px] sm:text-xs mt-1">Wait for the next fetch cycle or add more DEXes</p>
        </div>

        <!-- Empty State: Not enough DEXes selected -->
        <div v-else-if="!hasEnoughDexes" class="p-8 sm:p-12 text-center">
          <div class="w-12 h-12 sm:w-16 sm:h-16 mx-auto mb-3 sm:mb-4 rounded-full bg-red-500/10 flex items-center justify-center">
            <svg class="w-6 h-6 sm:w-8 sm:h-8 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-xs sm:text-sm">Select at least 2 DEXes</p>
          <p class="text-neutral-600 text-[10px] sm:text-xs mt-1">Arbitrage requires at least 2 platforms to compare rates</p>
          <button
            @click="selectAllDexes"
            class="mt-3 sm:mt-4 px-3 sm:px-4 py-1.5 sm:py-2 text-[10px] sm:text-xs font-medium text-emerald-400 border border-emerald-500/30 rounded-md sm:rounded-lg hover:bg-emerald-500/10 transition-colors"
          >
            Select All DEXes
          </button>
        </div>

        <!-- Empty State: No search results -->
        <div v-else-if="filteredOpportunities.length === 0" class="p-8 sm:p-12 text-center">
          <div class="w-12 h-12 sm:w-16 sm:h-16 mx-auto mb-3 sm:mb-4 rounded-full bg-white/5 flex items-center justify-center">
            <svg class="w-6 h-6 sm:w-8 sm:h-8 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-xs sm:text-sm">No results found</p>
          <p class="text-neutral-600 text-[10px] sm:text-xs mt-1">Try another ticker or adjust DEX filters</p>
          <button
            @click="searchQuery = ''; selectAllDexes(); handleSearch()"
            class="mt-3 sm:mt-4 px-3 sm:px-4 py-1.5 sm:py-2 text-[10px] sm:text-xs font-medium text-emerald-400 border border-emerald-500/30 rounded-md sm:rounded-lg hover:bg-emerald-500/10 transition-colors"
          >
            Reset Filters
          </button>
        </div>
        <!-- ========== DATA VIEWS (Cards on mobile, Table on desktop) ========== -->
        <div v-else>
          <!-- CARDS VIEW (Small screens < lg) -->
          <div class="lg:hidden p-3 sm:p-4 space-y-3">
          <div
            v-for="opp in paginatedOpportunities"
            :key="'card-' + opp.symbol"
            @click="isPro ? selectOpportunity(opp) : null"
            :class="[
              'group relative p-4 rounded-2xl border backdrop-blur-xl transition-all duration-300 cursor-pointer',
              'bg-gradient-to-br from-white/[0.08] to-white/[0.02]',
              'hover:from-white/[0.12] hover:to-white/[0.05]',
              'hover:shadow-lg hover:shadow-emerald-500/5',
              selectedOpportunity?.symbol === opp.symbol
                ? 'border-emerald-500/50 shadow-lg shadow-emerald-500/10 ring-1 ring-emerald-500/20'
                : 'border-white/10 hover:border-white/20'
            ]"
          >
            <!-- Glass reflection effect -->
            <div class="absolute inset-0 rounded-2xl overflow-hidden pointer-events-none">
              <div class="absolute -top-1/2 -left-1/2 w-full h-full bg-gradient-to-br from-white/10 to-transparent rotate-12 opacity-50"></div>
            </div>

            <!-- Card Header: Symbol + Daily Return -->
            <div class="relative flex items-center justify-between gap-2 mb-3">
              <div class="flex items-center gap-3 min-w-0 flex-1">
                <!-- Icon -->
                <div class="relative shrink-0">
                  <img
                    v-if="!isImageFailed(opp.symbol)"
                    :src="getFallbackIcon(opp.symbol)"
                    :alt="getBaseSymbol(opp.symbol)"
                    class="w-10 h-10 rounded-xl border border-white/10 bg-neutral-800/50 object-cover shadow-lg"
                    @error="(e) => handleImageError(opp.symbol, e)"
                  >
                  <div
                    v-else
                    class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-sm text-white font-bold border border-white/10 shadow-lg"
                  >
                    {{ getBaseSymbol(opp.symbol).charAt(0).toUpperCase() }}
                  </div>
                  <!-- Selection indicator -->
                  <div
                    v-if="selectedOpportunity?.symbol === opp.symbol"
                    class="absolute -bottom-1 -right-1 w-4 h-4 bg-emerald-500 rounded-full flex items-center justify-center ring-2 ring-neutral-900"
                  >
                    <svg class="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
                    </svg>
                  </div>
                </div>
                <!-- Symbol name -->
                <div class="min-w-0">
                  <h4 class="text-sm font-semibold text-white group-hover:text-emerald-300 transition-colors truncate">
                    {{ opp.symbol.replace('-USD-PERP', '') }}
                  </h4>
                  <p class="text-[10px] text-neutral-500">Perpetual</p>
                </div>
              </div>

              <!-- Pro Badge (only for non-Pro users) -->
              <div
                v-if="!isPro"
                class="group/pro relative flex items-center gap-1 px-2 py-1 rounded-lg bg-gradient-to-r from-amber-500/20 to-orange-500/20 border border-amber-500/30 cursor-pointer hover:from-amber-500/30 hover:to-orange-500/30 transition-all"
              >
                <svg class="w-3 h-3 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd"/>
                </svg>
                <span class="text-[9px] font-bold text-amber-400 uppercase tracking-wide">Pro</span>
                <!-- Tooltip on hover -->
                <div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 opacity-0 invisible group-hover/pro:opacity-100 group-hover/pro:visible transition-all duration-200 z-50 pointer-events-none">
                  <div class="px-3 py-2 bg-neutral-800 border border-amber-500/30 rounded-xl shadow-xl whitespace-nowrap">
                    <p class="text-[10px] font-semibold text-amber-400 mb-0.5">Pro Feature</p>
                    <p class="text-[9px] text-neutral-400">Arbitrage Execution requires Pro plan</p>
                  </div>
                  <div class="absolute top-full left-1/2 -translate-x-1/2 -mt-1 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-neutral-800"></div>
                </div>
              </div>

              <!-- Daily Return Badge -->
              <div :class="[
                'px-3 py-1.5 rounded-xl text-xs font-bold',
                opp.projectedDailyReturn >= 0
                  ? 'bg-emerald-500/15 text-emerald-400 ring-1 ring-emerald-500/30'
                  : 'bg-red-500/15 text-red-400 ring-1 ring-red-500/30'
              ]">
                {{ opp.projectedDailyReturn >= 0 ? '+' : '' }}{{ (opp.projectedDailyReturn).toFixed(4) }}%
                <span class="text-[9px] opacity-70 ml-0.5">/day</span>
              </div>
            </div>

            <!-- Route: Long -> Short -->
            <div class="relative flex flex-col sm:flex-row items-stretch sm:items-center gap-1.5 sm:gap-2 mb-3 p-2 sm:p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
              <a
                :href="getDexUrl(opp.longDex, opp.symbol)"
                target="_blank"
                @click.stop
                class="flex-1 flex items-center gap-2 px-2.5 sm:px-3 py-1.5 sm:py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 hover:bg-emerald-500/20 transition-colors min-w-0"
              >
                <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse shrink-0"></span>
                <span class="text-[10px] text-emerald-400 font-medium shrink-0">LONG</span>
                <span class="text-xs text-white font-semibold ml-auto truncate">{{ opp.longDex }}</span>
              </a>
              <!-- Arrow: vertical on mobile, horizontal on desktop -->
              <div class="flex items-center justify-center w-6 h-6 rounded-full bg-white/5 shrink-0 self-center">
                <!-- Down arrow (mobile) -->
                <svg class="w-3 h-3 text-neutral-500 sm:hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"/>
                </svg>
                <!-- Right arrow (desktop) -->
                <svg class="w-3 h-3 text-neutral-500 hidden sm:block" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6"/>
                </svg>
              </div>
              <a
                :href="getDexUrl(opp.shortDex, opp.symbol)"
                target="_blank"
                @click.stop
                class="flex-1 flex items-center gap-2 px-2.5 sm:px-3 py-1.5 sm:py-2 rounded-lg bg-red-500/10 border border-red-500/20 hover:bg-red-500/20 transition-colors min-w-0"
              >
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse shrink-0"></span>
                <span class="text-[10px] text-red-400 font-medium shrink-0">SHORT</span>
                <span class="text-xs text-white font-semibold ml-auto truncate">{{ opp.shortDex }}</span>
              </a>
            </div>

            <!-- Rates -->
            <div class="relative grid grid-cols-2 gap-2">
              <div class="p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
                <p class="text-[9px] text-neutral-500 uppercase tracking-wider mb-1">Long Rate</p>
                <p :class="['text-sm font-semibold', formatRate(opp.longRate).class]">
                  {{ formatRate(opp.longRate).value }}
                </p>
              </div>
              <div class="p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
                <p class="text-[9px] text-neutral-500 uppercase tracking-wider mb-1">Short Rate</p>
                <p :class="['text-sm font-semibold', formatRate(opp.shortRate).class]">
                  {{ formatRate(opp.shortRate).value }}
                </p>
              </div>
            </div>
          </div>
          </div>

          <!-- TABLE VIEW (Large screens >= lg) -->
          <div class="hidden lg:block overflow-x-auto">
          <table class="w-full">
          <thead class="bg-white/5">
            <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
              <th
                @click="toggleSort('symbol')"
                class="px-4 py-3 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Pair
                  <svg v-if="getSortState('symbol') === 'neutral'" class="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
                  </svg>
                  <svg v-else-if="getSortState('symbol') === 'asc'" class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"/>
                  </svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </span>
              </th>
              <th class="px-4 py-3 font-medium">Route</th>
              <th
                @click="toggleSort('longRate')"
                class="px-4 py-3 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Long Rate
                  <svg v-if="getSortState('longRate') === 'neutral'" class="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
                  </svg>
                  <svg v-else-if="getSortState('longRate') === 'asc'" class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"/>
                  </svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </span>
              </th>
              <th
                @click="toggleSort('shortRate')"
                class="px-4 py-3 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Short Rate
                  <svg v-if="getSortState('shortRate') === 'neutral'" class="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
                  </svg>
                  <svg v-else-if="getSortState('shortRate') === 'asc'" class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"/>
                  </svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </span>
              </th>
              <th
                @click="toggleSort('projectedDailyReturn')"
                class="px-2 py-3 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Daily Return
                  <svg v-if="getSortState('projectedDailyReturn') === 'neutral'" class="w-3 h-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
                  </svg>
                  <svg v-else-if="getSortState('projectedDailyReturn') === 'asc'" class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"/>
                  </svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </span>
              </th>
              <th class="w-16 pl-2 pr-4 py-3 font-medium"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr
              v-for="opp in paginatedOpportunities"
              :key="'table-' + opp.symbol"
              class="group hover:bg-white/5 transition-colors"
            >
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <img
                    v-if="!isImageFailed(opp.symbol)"
                    :src="getFallbackIcon(opp.symbol)"
                    :alt="getBaseSymbol(opp.symbol)"
                    class="w-6 h-6 rounded-full border border-black bg-neutral-800 shrink-0 object-cover"
                    @error="(e) => handleImageError(opp.symbol, e)"
                  >
                  <div
                    v-else
                    class="w-6 h-6 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-[10px] text-white font-bold border border-black shrink-0"
                  >
                    {{ getBaseSymbol(opp.symbol).charAt(0).toUpperCase() }}
                  </div>
                  <span class="text-xs font-medium text-neutral-300 group-hover:text-white">
                    {{ opp.symbol.replace('-USD-PERP', '') }}
                  </span>
                </div>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2 text-xs text-neutral-400">
                  <a
                    :href="getDexUrl(opp.longDex, opp.symbol)"
                    target="_blank"
                    class="text-emerald-400 hover:text-emerald-300 hover:underline transition-all flex items-center gap-1"
                  >
                    Long {{ opp.longDex }}
                    <svg class="w-2.5 h-2.5 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                  </a>
                  <span class="text-neutral-600">→</span>
                  <a
                    :href="getDexUrl(opp.shortDex, opp.symbol)"
                    target="_blank"
                    class="text-red-400 hover:text-red-300 hover:underline transition-all flex items-center gap-1"
                  >
                    Short {{ opp.shortDex }}
                    <svg class="w-2.5 h-2.5 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                  </a>
                </div>
              </td>
              <td class="px-4 py-3">
                <span :class="['text-xs font-medium', formatRate(opp.longRate).class]">
                  {{ formatRate(opp.longRate).value }}
                </span>
              </td>
              <td class="px-4 py-3">
                <span :class="['text-xs font-medium', formatRate(opp.shortRate).class]">
                  {{ formatRate(opp.shortRate).value }}
                </span>
              </td>
              <td class="px-2 py-3">
                <span :class="['text-xs font-medium', opp.projectedDailyReturn >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ opp.projectedDailyReturn >= 0 ? '+' : '' }}{{ (opp.projectedDailyReturn).toFixed(4) }}%
                </span>
              </td>
              <td class="w-16 pl-2 pr-4 py-3">
                <div class="inline-block relative">
                  <button
                    @click="isPro ? selectOpportunity(opp) : null"
                    :class="[
                      'w-8 h-8 rounded-full border flex items-center justify-center transition-all duration-200 peer',
                      selectedOpportunity?.symbol === opp.symbol
                        ? 'bg-emerald-500 text-white border-emerald-500 shadow-lg shadow-emerald-500/30'
                        : isPro
                          ? 'border-white/10 text-neutral-400 hover:text-emerald-400 hover:bg-emerald-500/10 hover:border-emerald-500/40 hover:scale-110'
                          : 'border-white/10 text-neutral-500 hover:text-neutral-400 cursor-not-allowed'
                    ]"
                  >
                    <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M8 5v14l11-7z"/>
                    </svg>
                  </button>
                  <div
                    v-if="!isPro"
                    class="absolute -top-1 -right-1 w-4 h-4 bg-gradient-to-br from-amber-500 to-orange-500 rounded-full flex items-center justify-center shadow-md"
                  >
                    <svg class="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd"/>
                    </svg>
                  </div>
                  <div
                    v-if="!isPro"
                    class="absolute right-0 bottom-full mb-2 opacity-0 invisible peer-hover:opacity-100 peer-hover:visible transition-all duration-150 z-50"
                  >
                    <div class="px-2.5 py-1 bg-neutral-800 text-amber-400 text-[10px] font-semibold rounded-md border border-amber-500/30 whitespace-nowrap shadow-xl">
                      Pro
                    </div>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
          </div>
        </div>

        <!-- Pagination Controls -->
        <div class="px-4 sm:px-6 py-3 sm:py-4 border-t border-white/5 flex items-center justify-between" v-if="totalPages > 1">
          <button
            @click="prevPage"
            :disabled="currentPage === 1"
            class="px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-medium rounded-md sm:rounded-lg border border-white/10 text-neutral-400 hover:text-white hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <span class="hidden sm:inline">Previous</span>
            <span class="sm:hidden">←</span>
          </button>

          <span class="text-[10px] sm:text-xs text-neutral-500">Page {{ currentPage }} / {{ totalPages }}</span>

          <button
            @click="nextPage"
            :disabled="currentPage === totalPages"
            class="px-2 sm:px-3 py-1 text-[10px] sm:text-xs font-medium rounded-md sm:rounded-lg border border-white/10 text-neutral-400 hover:text-white hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <span class="hidden sm:inline">Next</span>
            <span class="sm:hidden">→</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Trade Execution Panel -->
    <div :class="['glass-card rounded-2xl sm:rounded-3xl p-4 sm:p-6 flex flex-col h-full relative overflow-hidden', isPro ? 'border border-white/10' : 'border border-amber-500/20']">
      <!-- Background Glow -->
      <div v-if="selectedOpportunity && isPro" class="absolute top-0 right-0 w-48 sm:w-64 h-48 sm:h-64 bg-indigo-500/10 rounded-full blur-3xl -z-10 animate-pulse"></div>
      <div v-if="!isPro" class="absolute top-0 right-0 w-48 sm:w-64 h-48 sm:h-64 bg-amber-500/10 rounded-full blur-3xl -z-10"></div>

      <h3 class="text-sm sm:text-base font-medium text-white mb-4 sm:mb-6 flex items-center gap-2">
        <svg :class="['w-4 h-4 sm:w-5 sm:h-5', isPro ? 'text-indigo-400' : 'text-amber-400']" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path v-if="isPro" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
            <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
        </svg>
        Arbitrage Execution
        <span v-if="!isPro" class="ml-2 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-full">
          Pro
        </span>
      </h3>

      <!-- PRO RESTRICTION - Shown to non-Pro users -->
      <div v-if="!isPro" class="flex-1 flex flex-col items-center justify-center text-center p-4 sm:p-6 bg-gradient-to-b from-amber-500/5 to-orange-500/5 border border-amber-500/20 rounded-xl sm:rounded-2xl">
        <!-- Lock Icon -->
        <div class="w-12 h-12 sm:w-16 sm:h-16 bg-gradient-to-br from-amber-500/20 to-orange-500/20 rounded-full flex items-center justify-center mb-4 sm:mb-5 ring-2 ring-amber-500/30">
          <svg class="w-6 h-6 sm:w-8 sm:h-8 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
          </svg>
        </div>

        <!-- Title -->
        <h4 class="text-base sm:text-lg font-bold text-white mb-1.5 sm:mb-2">
          Pro Feature
        </h4>

        <!-- Description -->
        <p class="text-xs sm:text-sm text-neutral-400 mb-4 sm:mb-6 max-w-[280px] leading-relaxed">
          Automatic arbitrage trade execution is available exclusively for <span class="text-amber-400 font-medium">Pro</span> users.
        </p>

        <!-- Features List -->
        <div class="w-full mb-4 sm:mb-6 space-y-1.5 sm:space-y-2">
          <div class="flex items-center gap-2 text-[10px] sm:text-xs text-neutral-500">
            <svg class="w-3 h-3 sm:w-4 sm:h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            Automatic Long + Short execution
          </div>
          <div class="flex items-center gap-2 text-[10px] sm:text-xs text-neutral-500">
            <svg class="w-3 h-3 sm:w-4 sm:h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            Multi-DEX parallel execution
          </div>

        </div>

        <!-- CTA Button -->
        <button
          @click="showProModal = true"
          class="w-full py-3 sm:py-3.5 bg-gradient-to-r from-amber-500 to-orange-500 text-white text-xs sm:text-sm font-bold rounded-lg sm:rounded-xl hover:shadow-lg hover:shadow-amber-500/25 hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
          </svg>
          Upgrade to Pro
        </button>

        <!-- Footer Note -->
        <p class="text-[10px] text-neutral-600 mt-4">
          Access all features on
          <a href="https://cactusfund.app" target="_blank" class="text-amber-500 hover:underline">cactusfund.app</a>
        </p>
      </div>

      <!-- EXECUTION PANEL - Shown to Pro/Admin users -->
      <div v-else-if="selectedOpportunity" class="flex-1 flex flex-col">
        <!-- Strategy Header -->
        <div class="mb-4 sm:mb-6 p-3 sm:p-4 rounded-lg sm:rounded-xl bg-white/5 border border-white/5 flex items-center justify-between">
           <div>
              <div class="text-base sm:text-lg font-bold text-white tracking-wide">{{ selectedOpportunity.symbol }}</div>
              <div class="text-[10px] sm:text-xs text-neutral-400 mt-0.5 sm:mt-1">Neutral Strategy</div>
           </div>

           <div class="text-right">
             <div class="text-[10px] sm:text-xs text-emerald-400 font-mono bg-emerald-500/10 px-1.5 sm:px-2 py-0.5 sm:py-1 rounded">
                +{{ (selectedOpportunity.projectedDailyReturn || 0).toFixed(4) }}% / day
             </div>
           </div>
        </div>

        <!-- Dual Legs -->
        <div class="space-y-3 sm:space-y-4 mb-4 sm:mb-6">
            <!-- Leg A: Long -->
            <div class="p-2.5 sm:p-3 bg-emerald-500/5 border border-emerald-500/20 rounded-lg sm:rounded-xl relative group">
                <div class="absolute -left-0.5 sm:-left-1 top-3 sm:top-4 w-1.5 sm:w-2 h-6 sm:h-8 bg-emerald-500 rounded-r opacity-50"></div>

                <div class="flex justify-between items-center mb-1.5 sm:mb-2">
                    <span class="text-[10px] sm:text-xs font-bold text-emerald-400 uppercase tracking-wider">Long Leg (Buy)</span>
                    <span class="text-[8px] sm:text-[10px] text-neutral-500 font-mono">{{ selectedOpportunity.longDex }}</span>
                </div>

                <div class="flex items-center justify-between">
                     <span class="text-xs sm:text-sm text-white">Market Order</span>

                     <!-- Status Leg A -->
                     <span v-if="executionLogA" :class="['text-[10px] sm:text-xs px-1.5 sm:px-2 py-0.5 rounded', executionLogA.success ? 'bg-emerald-500 text-white' : executionLogA.success === false ? 'bg-red-500 text-white' : 'bg-yellow-500/20 text-yellow-400']">
                        {{ executionLogA.success ? 'FILLED' : (executionLogA.success === false ? 'FAILED' : 'PENDING') }}
                     </span>
                     <span v-else class="text-[10px] sm:text-xs text-neutral-600">Ready</span>
                </div>
                <!-- Error Message A -->
                <div v-if="executionLogA && executionLogA.success === false" class="mt-1.5 sm:mt-2 text-[9px] sm:text-[10px] text-red-400 bg-red-950/30 p-1 sm:p-1.5 rounded border border-red-500/20">
                    {{ executionLogA.message }}
                </div>
            </div>

            <!-- Loop Icon -->
            <div class="flex justify-center -my-1.5 sm:-my-2 relative z-10">
                <div class="bg-[#0f0f13] border border-white/10 rounded-full p-1 sm:p-1.5 shadow-xl">
                    <svg class="w-3 h-3 sm:w-4 sm:h-4 text-neutral-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
                    </svg>
                </div>
            </div>

            <!-- Leg B: Short -->
            <div class="p-2.5 sm:p-3 bg-red-500/5 border border-red-500/20 rounded-lg sm:rounded-xl relative">
                <div class="absolute -left-0.5 sm:-left-1 top-3 sm:top-4 w-1.5 sm:w-2 h-6 sm:h-8 bg-red-500 rounded-r opacity-50"></div>

                <div class="flex justify-between items-center mb-1.5 sm:mb-2">
                    <span class="text-[10px] sm:text-xs font-bold text-red-400 uppercase tracking-wider">Short Leg (Sell)</span>
                    <span class="text-[8px] sm:text-[10px] text-neutral-500 font-mono">{{ selectedOpportunity.shortDex }}</span>
                </div>

                <div class="flex items-center justify-between">
                     <span class="text-xs sm:text-sm text-white">Market Order</span>

                     <!-- Status Leg B -->
                     <span v-if="executionLogB" :class="['text-[10px] sm:text-xs px-1.5 sm:px-2 py-0.5 rounded', executionLogB.success ? 'bg-emerald-500 text-white' : executionLogB.success === false ? 'bg-red-500 text-white' : 'bg-yellow-500/20 text-yellow-400']">
                        {{ executionLogB.success ? 'FILLED' : (executionLogB.success === false ? 'FAILED' : 'PENDING') }}
                     </span>
                     <span v-else class="text-[10px] sm:text-xs text-neutral-600">Ready</span>
                </div>
                 <!-- Error Message B -->
                <div v-if="executionLogB && executionLogB.success === false" class="mt-1.5 sm:mt-2 text-[9px] sm:text-[10px] text-red-400 bg-red-950/30 p-1 sm:p-1.5 rounded border border-red-500/20">
                    {{ executionLogB.message }}
                </div>
            </div>
        </div>

        <!-- Size Input -->
        <div class="mb-3 sm:mb-4">
            <label class="text-[10px] sm:text-xs text-neutral-400 mb-1 sm:mb-1.5 block">Trade Size (USD per Leg)</label>
            <div class="relative group">
              <span class="absolute left-2.5 sm:left-3 top-2 sm:top-2.5 text-[10px] sm:text-xs text-neutral-500 group-focus-within:text-indigo-400 transition-colors">$</span>
              <input
                v-model="tradeSize"
                type="number"
                class="w-full pl-5 sm:pl-6 pr-3 py-2.5 sm:py-3 bg-white/5 border border-white/10 rounded-lg sm:rounded-xl text-xs sm:text-sm text-white focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/20 transition-all font-mono"
                placeholder="100"
              >
            </div>
            <div class="text-[9px] sm:text-[10px] text-neutral-600 mt-1 text-right">
                Total Exposure: ${{ tradeSize * 2 }}
            </div>
        </div>

        <!-- Execute Button -->
        <div class="mt-auto pt-1 sm:pt-2">
          <button
            @click="executeTrade"
            :disabled="isExecuting || !isConnected"
            class="w-full py-3 sm:py-3.5 bg-gradient-to-r from-indigo-600 to-purple-600 text-white text-xs sm:text-sm font-bold rounded-lg sm:rounded-xl hover:shadow-lg hover:shadow-indigo-500/20 hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none transition-all flex items-center justify-center gap-2 group"
          >
            <svg v-if="isExecuting" class="animate-spin h-3 w-3 sm:h-4 sm:w-4 text-white/80" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <div v-else class="flex flex-col items-center leading-tight">
               <span v-if="!isConnected" class="text-xs sm:text-sm">Connect Wallet</span>
               <span v-else class="text-xs sm:text-sm">Execute Strategy</span>
               <span v-if="isConnected" class="text-[9px] sm:text-[10px] font-normal opacity-70">Long + Short</span>
            </div>
          </button>

          <!-- Global Status -->
          <div v-if="executionLogA && executionLogB && executionLogA.success && executionLogB.success" class="mt-3 sm:mt-4 p-2.5 sm:p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-lg sm:rounded-xl text-center">
             <div class="text-xs sm:text-sm font-bold text-emerald-400 flex items-center justify-center gap-1.5 sm:gap-2">
                <svg class="w-3 h-3 sm:w-4 sm:h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                Strategy Executed!
             </div>
             <div class="text-[9px] sm:text-[10px] text-emerald-400/70 mt-0.5 sm:mt-1">Both legs filled. Check positions on exchanges.</div>
          </div>
        </div>

      </div>

      <!-- Empty State (Pro users only) -->
      <div v-else class="flex-1 flex flex-col items-center justify-center text-center p-4 sm:p-6 opacity-40">
        <div class="w-12 h-12 sm:w-16 sm:h-16 bg-white/5 rounded-full flex items-center justify-center mb-3 sm:mb-4">
             <svg class="w-6 h-6 sm:w-8 sm:h-8 text-neutral-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path>
            </svg>
        </div>
        <h4 class="text-white font-medium mb-0.5 sm:mb-1 text-sm sm:text-base">Select Strategy</h4>
        <p class="text-[10px] sm:text-xs text-neutral-400 max-w-[200px]">
            Choose an opportunity from the list to load execution parameters.
        </p>
      </div>
    </div>
  </div>

  <!-- Pro Plan Modal -->
  <Teleport to="body">
    <Transition name="modal">
      <div 
        v-if="showProModal" 
        class="fixed inset-0 z-[100] flex items-center justify-center p-4"
        @click.self="showProModal = false"
      >
        <!-- Backdrop -->\n        <div class="absolute inset-0 bg-black/70 backdrop-blur-sm" @click="showProModal = false"></div>
        
        <!-- Modal Content -->
        <div class="relative w-full max-w-lg glass-card rounded-3xl p-6 sm:p-8 border border-amber-500/20 overflow-hidden animate-modal-enter">
          <!-- Background Glow -->
          <div class="absolute top-0 right-0 w-64 h-64 bg-amber-500/10 rounded-full blur-3xl -z-10"></div>
          <div class="absolute bottom-0 left-0 w-48 h-48 bg-orange-500/10 rounded-full blur-3xl -z-10"></div>
          
          <!-- Close Button -->
          <button 
            @click="showProModal = false"
            class="absolute top-4 right-4 w-8 h-8 bg-white/5 hover:bg-white/10 rounded-full flex items-center justify-center text-neutral-400 hover:text-white transition-colors"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>

          <!-- Coming Soon Badge -->
          <div class="flex justify-center mb-5">
            <span class="px-4 py-1.5 text-sm font-bold uppercase tracking-wider bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-full">
              Coming Soon
            </span>
          </div>

          <!-- Main Message -->
          <div class="text-center mb-6">
            <div class="w-16 h-16 mx-auto mb-4 bg-gradient-to-br from-amber-500/20 to-orange-500/20 rounded-2xl flex items-center justify-center ring-2 ring-amber-500/30">
              <svg class="w-8 h-8 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
              </svg>
            </div>
            <h2 class="text-xl font-bold text-white mb-2">Pro Plan</h2>
            <p class="text-neutral-400 text-sm leading-relaxed max-w-sm mx-auto">
              We're working hard to bring you the Pro plan. Stay tuned for the official launch!
            </p>
          </div>

          <!-- Features Preview -->
          <div class="bg-white/5 rounded-2xl p-5 mb-6">
            <h3 class="text-xs font-bold text-white uppercase tracking-wider mb-3 flex items-center gap-2">
              <svg class="w-4 h-4 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
              </svg>
              Included Features
            </h3>
            <ul class="space-y-2.5">
              <li class="flex items-center gap-3 text-sm text-neutral-300">
                <div class="w-5 h-5 rounded-full bg-emerald-500/20 flex items-center justify-center flex-shrink-0">
                  <svg class="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
                  </svg>
                </div>
                Automatic Arbitrage Execution (Long + Short)
              </li>
              <li class="flex items-center gap-3 text-sm text-neutral-300">
                <div class="w-5 h-5 rounded-full bg-emerald-500/20 flex items-center justify-center flex-shrink-0">
                  <svg class="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
                  </svg>
                </div>
                Multi-DEX Parallel Execution
              </li>
              <li class="flex items-center gap-3 text-sm text-neutral-300">
                <div class="w-5 h-5 rounded-full bg-amber-500/20 flex items-center justify-center flex-shrink-0">
                  <svg class="w-3 h-3 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                  </svg>
                </div>
                More features coming soon...
              </li>
            </ul>
          </div>

          <!-- Close CTA -->
          <button 
            @click="showProModal = false"
            class="w-full py-3 bg-white/5 border border-white/10 text-neutral-400 text-sm font-medium rounded-xl hover:bg-white/10 hover:text-white transition-colors"
          >
            Got it!
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Modal animations */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

@keyframes modal-enter {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.animate-modal-enter {
  animation: modal-enter 0.3s ease-out;
}
</style>


