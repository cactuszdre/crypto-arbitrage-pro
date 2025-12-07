<script setup>
import { ref, onMounted, onUnmounted, computed, watch, unref } from 'vue'
import { arbitrageApi } from '../services/api'
import { useAppKitAccount, useAppKitProvider } from '@reown/appkit/vue'
import { executeHyperliquidOrder, executeParadexOrder, executeLighterOrder } from '../services/execution'

const opportunities = ref([])
const currentPage = ref(1)
const itemsPerPage = 8
const failedImages = ref(new Set())
const searchQuery = ref('')

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
const selectedPeriod = ref('current') // 'current', '1d', '7d', '30d'
const periodOptions = [
  { value: 'current', label: 'Current' },
  { value: '1d', label: '1 Day Avg' },
  { value: '7d', label: '7 Days Avg' },
  { value: '30d', label: '30 Days Avg' }
]

// DEX filter - all DEXes selected by default
const selectedDexes = ref([]) // Will be initialized with all DEXes on mount
const showDexFilter = ref(false)
const dexFilterInitialized = ref(false)

// Get unique DEXes from opportunities
const availableDexes = computed(() => {
  const dexSet = new Set()
  opportunities.value.forEach(opp => {
    if (opp.longDex) dexSet.add(opp.longDex)
    if (opp.shortDex) dexSet.add(opp.shortDex)
  })
  return Array.from(dexSet).sort()
})

// Initialize selectedDexes with all available DEXes when data loads
watch(availableDexes, (newDexes) => {
  if (!dexFilterInitialized.value && newDexes.length > 0) {
    selectedDexes.value = [...newDexes]
    dexFilterInitialized.value = true
  }
}, { immediate: true })

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

// Filter opportunities based on search query and DEX filter
const filteredOpportunities = computed(() => {
  // If less than 2 DEXes selected, return empty (need at least 2 for arbitrage)
  if (selectedDexes.value.length < 2) {
    return []
  }
  
  let result = opportunities.value
  
  // Apply DEX filter - only show opportunities where BOTH dexes are selected
  // Always apply if not all DEXes are selected
  if (selectedDexes.value.length < availableDexes.value.length) {
    result = result.filter(opp => 
      selectedDexes.value.includes(opp.longDex) && 
      selectedDexes.value.includes(opp.shortDex)
    )
  }
  
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

// Fetch opportunities
// Fetch opportunities
const fetchOpportunities = async () => {
  try {
    const response = await arbitrageApi.getOpportunities()
    opportunities.value = response.data
  } catch (error) {
    console.error('Failed to fetch opportunities:', error)
  }
}

// Generate DEX URL
const getDexUrl = (dexName, symbol) => {
  const base = getBaseSymbol(symbol)
  
  switch(dexName.toLowerCase()) {
    case 'paradex':
      return `https://app.paradex.trade/trade/${symbol}`
    case 'hyperliquid':
      return `https://app.hyperliquid.xyz/trade/${base}`
    case 'lighter':
      return `https://app.lighter.xyz/trade/${base}_USDC`
    case 'dydx':
      return `https://dydx.exchange/trade/${base}-USD`
    default:
      return '#'
  }
}

// Auto-refresh every 10 seconds
let refreshInterval = null

onMounted(() => {
  fetchOpportunities()
  refreshInterval = setInterval(() => {
    fetchOpportunities()
  }, 10000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
    
    <!-- Opportunities List -->
    <div class="lg:col-span-2 space-y-6">
      <div class="glass-card rounded-3xl overflow-hidden">
        <div class="px-6 py-5 border-b border-white/5">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-base font-medium text-white">Live Opportunities</h3>
            <span class="text-xs text-indigo-400">
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
              placeholder="Search by ticker (BTC, ETH, SOL...) or DEX..."
              class="w-full pl-10 pr-10 py-2.5 bg-white/5 border border-white/10 rounded-xl text-sm text-white placeholder-neutral-500 focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/50 transition-all"
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
          <div class="flex items-center gap-2 mt-4">
            <span class="text-xs text-neutral-500 mr-2">Period:</span>
            <button 
              v-for="option in periodOptions" 
              :key="option.value"
              @click="selectedPeriod = option.value"
              :class="[
                'px-3 py-1.5 text-xs font-medium rounded-lg transition-all',
                selectedPeriod === option.value 
                  ? 'bg-indigo-500/20 text-indigo-400 border border-indigo-500/30' 
                  : 'bg-white/5 text-neutral-400 border border-white/10 hover:bg-white/10 hover:text-white'
              ]"
            >
              {{ option.label }}
            </button>
            <span v-if="selectedPeriod !== 'current'" class="text-[10px] text-neutral-600 ml-2">
              (Avg data coming soon)
            </span>
            
            <!-- DEX Filter Dropdown -->
            <div class="relative ml-auto">
              <button 
                @click="showDexFilter = !showDexFilter"
                :class="[
                  'flex items-center gap-2 px-3 py-1.5 text-xs font-medium rounded-lg border transition-all',
                  !hasEnoughDexes 
                    ? 'bg-red-500/10 text-red-400 border-red-500/30' 
                    : 'bg-white/5 text-neutral-400 border-white/10 hover:bg-white/10 hover:text-white'
                ]"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z"></path>
                </svg>
                DEX Filter
                <span 
                  v-if="selectedDexes.length !== availableDexes.length" 
                  :class="[
                    'text-[10px] px-1.5 py-0.5 rounded-full',
                    hasEnoughDexes ? 'bg-indigo-500 text-white' : 'bg-red-500 text-white'
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
                    Sélectionnez au moins 2 DEX
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
                      class="w-4 h-4 rounded border-white/20 bg-white/5 text-indigo-500 focus:ring-indigo-500/50"
                    >
                    <span :class="isDexSelected(dex) ? 'text-white' : 'text-neutral-500'">{{ dex }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- Empty State: No data at all -->
        <div v-if="opportunities.length === 0" class="p-12 text-center">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-white/5 flex items-center justify-center">
            <svg class="w-8 h-8 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-sm">Pas assez de données pour le moment</p>
          <p class="text-neutral-600 text-xs mt-1">Attendez le prochain cycle de fetch ou ajoutez plus de DEX</p>
        </div>

        <!-- Empty State: Not enough DEXes selected -->
        <div v-else-if="!hasEnoughDexes" class="p-12 text-center">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-red-500/10 flex items-center justify-center">
            <svg class="w-8 h-8 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-sm">Sélectionnez au moins 2 DEX</p>
          <p class="text-neutral-600 text-xs mt-1">L'arbitrage nécessite au moins 2 plateformes pour comparer les taux</p>
          <button 
            @click="selectAllDexes"
            class="mt-4 px-4 py-2 text-xs font-medium text-indigo-400 border border-indigo-500/30 rounded-lg hover:bg-indigo-500/10 transition-colors"
          >
            Sélectionner tous les DEX
          </button>
        </div>

        <!-- Empty State: No search results -->
        <div v-else-if="filteredOpportunities.length === 0" class="p-12 text-center">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-white/5 flex items-center justify-center">
            <svg class="w-8 h-8 text-neutral-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
          </div>
          <p class="text-neutral-500 text-sm">Aucun résultat trouvé</p>
          <p class="text-neutral-600 text-xs mt-1">Essayez un autre ticker ou modifiez les filtres DEX</p>
          <button 
            @click="searchQuery = ''; selectAllDexes(); handleSearch()"
            class="mt-4 px-4 py-2 text-xs font-medium text-indigo-400 border border-indigo-500/30 rounded-lg hover:bg-indigo-500/10 transition-colors"
          >
            Réinitialiser les filtres
          </button>
        </div>

        <table v-else class="w-full">
          <thead class="bg-white/5">
            <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
              <th 
                @click="toggleSort('symbol')"
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
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
              <th class="px-6 py-4 font-medium">Route</th>
              <th 
                @click="toggleSort('longRate')"
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
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
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
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
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
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
              <th class="px-6 py-4 font-medium text-right"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr 
              v-for="opp in paginatedOpportunities" 
              :key="opp.symbol" 
              class="group hover:bg-white/5 transition-colors"
            >
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div class="flex -space-x-2">
                    <!-- Primary icon attempt -->
                    <img 
                      v-if="!isImageFailed(opp.symbol)"
                      :src="getFallbackIcon(opp.symbol)"
                      :alt="getBaseSymbol(opp.symbol)"
                      class="w-6 h-6 rounded-full border border-black transition-all bg-neutral-800"
                      @error="(e) => handleImageError(opp.symbol, e)"
                    >
                    <!-- Fallback: styled letter placeholder -->
                    <div 
                      v-if="isImageFailed(opp.symbol)"
                      class="w-6 h-6 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-[10px] text-white font-bold border border-black shadow-sm"
                    >
                      {{ getBaseSymbol(opp.symbol).charAt(0).toUpperCase() }}
                    </div>
                  </div>
                  <span class="text-xs font-medium text-neutral-300 group-hover:text-white">
                    {{ opp.symbol.replace('-USD-PERP', '') }}
                  </span>
                </div>
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center gap-2 text-xs text-neutral-400">
                  <a 
                    :href="getDexUrl(opp.longDex, opp.symbol)" 
                    target="_blank" 
                    class="text-emerald-400 hover:text-emerald-300 hover:underline decoration-emerald-500/30 underline-offset-2 transition-all flex items-center gap-1"
                  >
                    Long {{ opp.longDex }}
                    <svg class="w-2.5 h-2.5 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                  </a>
                  <span class="text-neutral-600">→</span>
                  <a 
                    :href="getDexUrl(opp.shortDex, opp.symbol)" 
                    target="_blank" 
                    class="text-red-400 hover:text-red-300 hover:underline decoration-red-500/30 underline-offset-2 transition-all flex items-center gap-1"
                  >
                    Short {{ opp.shortDex }}
                    <svg class="w-2.5 h-2.5 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path></svg>
                  </a>
                </div>
              </td>
              <td class="px-6 py-4">
                <span :class="['text-xs font-medium', formatRate(opp.longRate).class]">
                  {{ formatRate(opp.longRate).value }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span :class="['text-xs font-medium', formatRate(opp.shortRate).class]">
                  {{ formatRate(opp.shortRate).value }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span :class="['text-xs font-medium', opp.projectedDailyReturn >= 0 ? 'text-emerald-400' : 'text-red-400']">
                  {{ opp.projectedDailyReturn >= 0 ? '+' : '' }}{{ (opp.projectedDailyReturn).toFixed(4) }}%
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <button 
                  @click="selectOpportunity(opp)"
                  class="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center text-neutral-400 hover:text-white hover:bg-white/10 transition-colors"
                  :class="{ 'bg-indigo-500 text-white border-indigo-500': selectedOpportunity?.symbol === opp.symbol }"
                >
                  <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M8 5v14l11-7z"/>
                  </svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        
        <!-- Pagination Controls -->
        <div class="px-6 py-4 border-t border-white/5 flex items-center justify-between" v-if="totalPages > 1">
          <button 
            @click="prevPage" 
            :disabled="currentPage === 1"
            class="px-3 py-1 text-xs font-medium rounded-lg border border-white/10 text-neutral-400 hover:text-white hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Previous
          </button>
          
          <span class="text-xs text-neutral-500">Page {{ currentPage }} of {{ totalPages }}</span>
          
          <button 
            @click="nextPage" 
            :disabled="currentPage === totalPages"
            class="px-3 py-1 text-xs font-medium rounded-lg border border-white/10 text-neutral-400 hover:text-white hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Next
          </button>
        </div>
      </div>
    </div>

    <!-- Trade Execution Panel - PRO ONLY -->
    <div class="glass-card rounded-3xl p-6 flex flex-col h-full border border-amber-500/20 relative overflow-hidden">
      <!-- Background Glow -->
      <div class="absolute top-0 right-0 w-64 h-64 bg-amber-500/10 rounded-full blur-3xl -z-10"></div>

      <h3 class="text-base font-medium text-white mb-6 flex items-center gap-2">
        <svg class="w-5 h-5 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
        </svg>
        Arbitrage Execution
        <span class="ml-2 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-full">
          Pro
        </span>
      </h3>
      
      <!-- Pro Upgrade CTA -->
      <div class="flex-1 flex flex-col items-center justify-center text-center p-6 bg-gradient-to-b from-amber-500/5 to-orange-500/5 border border-amber-500/20 rounded-2xl">
        <!-- Lock Icon -->
        <div class="w-16 h-16 bg-gradient-to-br from-amber-500/20 to-orange-500/20 rounded-full flex items-center justify-center mb-5 ring-2 ring-amber-500/30">
          <svg class="w-8 h-8 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
          </svg>
        </div>
        
        <!-- Title -->
        <h4 class="text-lg font-bold text-white mb-2">
          Fonctionnalité Pro
        </h4>
        
        <!-- Description -->
        <p class="text-sm text-neutral-400 mb-6 max-w-[280px] leading-relaxed">
          L'exécution automatique des trades d'arbitrage n'est pas disponible dans la version <span class="text-amber-400 font-medium">open source</span>.
        </p>
        
        <!-- Features List -->
        <div class="w-full mb-6 space-y-2">
          <div class="flex items-center gap-2 text-xs text-neutral-500">
            <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            Exécution Long + Short automatique
          </div>
          <div class="flex items-center gap-2 text-xs text-neutral-500">
            <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            Multi-DEX en parallèle
          </div>
          <div class="flex items-center gap-2 text-xs text-neutral-500">
            <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            Support Hyperliquid, Paradex, Lighter
          </div>
        </div>
        
        <!-- CTA Button -->
        <a 
          href="https://github.com/cactuszdre/crypto-arbitrage-pro" 
          target="_blank"
          class="w-full py-3.5 bg-gradient-to-r from-amber-500 to-orange-500 text-white text-sm font-bold rounded-xl hover:shadow-lg hover:shadow-amber-500/25 hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-2"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
          </svg>
          Obtenir la version Pro
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path>
          </svg>
        </a>
        
        <!-- Footer Note -->
        <p class="text-[10px] text-neutral-600 mt-4">
          Accédez à toutes les fonctionnalités sur 
          <a href="https://github.com/cactuszdre/crypto-arbitrage-pro" target="_blank" class="text-amber-500 hover:underline">crypto-arbitrage-pro</a>
        </p>
      </div>
    </div>
  </div>
</template>
