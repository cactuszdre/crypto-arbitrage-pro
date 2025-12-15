<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick, watch, defineProps } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { fundingRateApi, arbitrageApi, dexApi } from '../services/api'
import FundingRateChart from '../components/FundingRateChart.vue'

// Props from router (when navigating from Arbitrage table)
const props = defineProps({
  initialSymbol: { type: String, default: null },
  initialLongDex: { type: String, default: null },
  initialShortDex: { type: String, default: null }
})

const route = useRoute()
const router = useRouter()

// Data
const fundingRates = ref([])
const loading = ref(true)
const error = ref(null)
const symbols = ref([]) // Available symbols
const availableDexes = ref([]) // Available DEXes from database
const rawRates = ref([]) // Raw rates for DEX-specific data

// Selected values
const selectedSymbol = ref('BTC-USD-PERP')
const selectedLongDex = ref(null)
const selectedShortDex = ref(null)
const positionSize = ref(10000) // Total position size in $

// Position size options
const positionSizeOptions = [
  { label: '$1k', value: 1000 },
  { label: '$10k', value: 10000 },
  { label: '$50k', value: 50000 },
  { label: '$100k', value: 100000 }
]

// Dropdowns state
const showSymbolDropdown = ref(false)
const showLongDexDropdown = ref(false)
const showShortDexDropdown = ref(false)
const symbolSearchQuery = ref('')
const symbolSearchInputRef = ref(null)

// ===== ANIMATED COUNTER SYSTEM =====
const displayRate = ref(0)
const displayRate8h = ref(0)
const displayRateAnnual = ref(0)
const targetRate = ref(0)
const targetRate8h = ref(0)
const targetRateAnnual = ref(0)
const ANIMATION_DURATION = 800

const easeOutExpo = (t) => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t))

const animateValue = (displayRef, startValue, endValue, duration = ANIMATION_DURATION) => {
  const startTime = performance.now()
  const difference = endValue - startValue
  
  const animate = (currentTime) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    const easedProgress = easeOutExpo(progress)
    
    displayRef.value = startValue + (difference * easedProgress)
    
    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      displayRef.value = endValue
    }
  }
  
  requestAnimationFrame(animate)
}

watch(targetRate, (newVal, oldVal) => {
  animateValue(displayRate, oldVal || 0, newVal || 0)
})

watch(targetRate8h, (newVal, oldVal) => {
  animateValue(displayRate8h, oldVal || 0, newVal || 0)
})

watch(targetRateAnnual, (newVal, oldVal) => {
  animateValue(displayRateAnnual, oldVal || 0, newVal || 0)
})

const formattedRate = computed(() => (displayRate.value * 100).toFixed(6) + '%')
const formattedRate8h = computed(() => displayRate8h.value.toFixed(4) + '%')
const formattedRateAnnual = computed(() => displayRateAnnual.value.toFixed(2) + '%')

// ===== COMPUTED PROPERTIES =====

// Get the symbol data object (contains rates array for all DEXes)
const symbolData = computed(() => {
  if (!selectedSymbol.value) return null
  const found = rawRates.value.find(r => r.symbol === selectedSymbol.value)
  return found
})

// Get rate data for selected symbol from Long DEX
const longDexRate = computed(() => {
  if (!symbolData.value || !selectedLongDex.value) {
    return null
  }
  const rateObj = symbolData.value.rates?.find(r => r.dex === selectedLongDex.value)
  if (!rateObj) return null
  return {
    dex: rateObj.dex,
    rateAnnual: parseFloat(rateObj.rateAnnual) || 0,
    rate8h: (parseFloat(rateObj.rateAnnual) || 0) / (365 * 3), // Convert annual to 8h
    rate: (parseFloat(rateObj.rateAnnual) || 0) / (365 * 3 * 100) // Convert to raw rate
  }
})

// Get rate data for selected symbol from Short DEX
const shortDexRate = computed(() => {
  if (!symbolData.value || !selectedShortDex.value) return null
  const rateObj = symbolData.value.rates?.find(r => r.dex === selectedShortDex.value)
  if (!rateObj) return null
  return {
    dex: rateObj.dex,
    rateAnnual: parseFloat(rateObj.rateAnnual) || 0,
    rate8h: (parseFloat(rateObj.rateAnnual) || 0) / (365 * 3), // Convert annual to 8h
    rate: (parseFloat(rateObj.rateAnnual) || 0) / (365 * 3 * 100) // Convert to raw rate
  }
})

// Calculate spread between the two DEXes
const spreadData = computed(() => {
  if (!longDexRate.value || !shortDexRate.value) return null
  
  const longRate = longDexRate.value.rate8h || 0
  const shortRate = shortDexRate.value.rate8h || 0
  const spread = Math.abs(longRate - shortRate)
  const dailyReturn = spread * 3 // 3 funding periods per day
  const annualReturn = dailyReturn * 365
  
  return {
    longRate,
    shortRate,
    spread,
    dailyReturn,
    annualReturn,
    isPositive: longRate < shortRate // Positive when long rate is lower (you earn paying less)
  }
})

// Update display values when DEX rates change
watch([longDexRate, shortDexRate], () => {
  if (longDexRate.value) {
    targetRate.value = longDexRate.value.rate || 0
    targetRate8h.value = longDexRate.value.rate8h || 0
    targetRateAnnual.value = longDexRate.value.rateAnnual || 0
  }
})

// Helper: Get DEX count for a symbol
const getDexCountForSymbol = (symbolStr) => {
  const symbolRates = rawRates.value.find(r => r.symbol === symbolStr)
  if (!symbolRates || !symbolRates.rates) return 0
  return symbolRates.rates.length
}

// Pinned symbols (BTC, ETH, SOL) - only if they have 2+ DEXes
const pinnedSymbols = computed(() => {
  return symbols.value.filter(s => 
    ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP'].includes(s.symbol) &&
    getDexCountForSymbol(s.symbol) >= 2
  )
})

const otherSymbols = computed(() => {
  return symbols.value
    .filter(s => 
      !['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP'].includes(s.symbol) &&
      getDexCountForSymbol(s.symbol) >= 2
    )
    .sort((a, b) => a.symbol.localeCompare(b.symbol))
})

const filteredOtherSymbols = computed(() => {
  const query = symbolSearchQuery.value.toLowerCase().trim()
  if (!query) return otherSymbols.value
  return otherSymbols.value.filter(s => s.symbol.toLowerCase().includes(query))
})

const isSelectedInOther = computed(() => {
  return otherSymbols.value.some(s => s.symbol === selectedSymbol.value)
})

const selectedSymbolObj = computed(() => {
  return symbols.value.find(s => s.symbol === selectedSymbol.value)
})

// ===== HELPER FUNCTIONS =====

const cryptoColors = {
  'BTC': '#F7931A', 'ETH': '#627EEA', 'SOL': '#14F195', 'XRP': '#23292F',
  'BNB': '#F3BA2F', 'ADA': '#0D1E30', 'DOGE': '#C3A634', 'DOT': '#E6007A',
  'AVAX': '#E84142', 'LINK': '#2A5ADA', 'MATIC': '#8247E5', 'UNI': '#FF007A'
}

const getCryptoColor = (symbol) => {
  const base = symbol?.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toUpperCase()
  return cryptoColors[base] || '#6366F1'
}

const getFallbackIcon = (symbol) => {
  if (!symbol) return null
  const base = symbol.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toUpperCase()
  return `https://app.paradex.trade/static/market/${base}.svg`
}

// Image fallback system
const failedImages = ref(new Set())

const getBaseSymbol = (symbol) => {
  if (!symbol) return ''
  return symbol.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toUpperCase()
}

const isImageFailed = (symbol) => {
  return failedImages.value.has(symbol)
}

const handleImageError = (symbol, event) => {
  event.target.style.display = 'none'
  failedImages.value.add(symbol)
}

const processSymbols = (rawSymbols) => {
  const seen = new Map()
  for (const item of rawSymbols) {
    if (!seen.has(item.symbol)) {
      seen.set(item.symbol, {
        symbol: item.symbol,
        iconUrl: getFallbackIcon(item.symbol)
      })
    }
  }
  return Array.from(seen.values())
}

// ===== SELECTION FUNCTIONS =====

const selectSymbol = async (symbolStr) => {
  if (symbolStr === selectedSymbol.value) return
  selectedSymbol.value = symbolStr
  showSymbolDropdown.value = false
  symbolSearchQuery.value = ''
  updateUrlParams()
}

const selectLongDex = (dex) => {
  selectedLongDex.value = dex
  showLongDexDropdown.value = false
  updateUrlParams()
}

const selectShortDex = (dex) => {
  selectedShortDex.value = dex
  showShortDexDropdown.value = false
  updateUrlParams()
}

// Swap the two DEXes
const swapDexes = () => {
  const temp = selectedLongDex.value
  selectedLongDex.value = selectedShortDex.value
  selectedShortDex.value = temp
  updateUrlParams()
}

const updateUrlParams = () => {
  router.replace({
    path: '/overview',
    query: {
      symbol: selectedSymbol.value,
      longDex: selectedLongDex.value,
      shortDex: selectedShortDex.value
    }
  })
}

const toggleSymbolDropdown = async () => {
  showSymbolDropdown.value = !showSymbolDropdown.value
  showLongDexDropdown.value = false
  showShortDexDropdown.value = false
  if (showSymbolDropdown.value) {
    await nextTick()
    symbolSearchInputRef.value?.focus()
  }
}

const toggleLongDexDropdown = () => {
  showLongDexDropdown.value = !showLongDexDropdown.value
  showSymbolDropdown.value = false
  showShortDexDropdown.value = false
}

const toggleShortDexDropdown = () => {
  showShortDexDropdown.value = !showShortDexDropdown.value
  showSymbolDropdown.value = false
  showLongDexDropdown.value = false
}

const closeDropdowns = (e) => {
  if (!e.target.closest('.dropdown-container')) {
    showSymbolDropdown.value = false
    showLongDexDropdown.value = false
    showShortDexDropdown.value = false
    symbolSearchQuery.value = ''
  }
}

// Get list of DEXes that actually have data for this symbol
const availableDexesForSymbol = computed(() => {
  if (!symbolData.value || !symbolData.value.rates) return []
  return symbolData.value.rates.map(r => r.dex).filter(dex => availableDexes.value.includes(dex))
})

// ===== DATA FETCHING =====

const loadAvailableDexes = async () => {
  try {
    const response = await dexApi.getAll()
    availableDexes.value = response.data.map(d => d.name)
  } catch (err) {
    console.error('Failed to load DEXes:', err)
  }
}

const fetchData = async () => {
  error.value = null
  try {
    const [ratesResponse, symbolsResponse, rawRatesResponse] = await Promise.all([
      fundingRateApi.getAll(),
      fundingRateApi.getAllSymbols(),
      arbitrageApi.getAllRates()
    ])
    
    fundingRates.value = ratesResponse.data
    rawRates.value = rawRatesResponse.data
    
    if (symbolsResponse.data && symbolsResponse.data.length > 0) {
      symbols.value = processSymbols(symbolsResponse.data)
    }

    // Validate selected symbol exists
    const exists = symbols.value.some(s => s.symbol === selectedSymbol.value)
    if (!exists && symbols.value.length > 0) {
      selectedSymbol.value = symbols.value[0].symbol
    }

    // Set Default DEXes based on data availability for this symbol
    const validDexes = availableDexesForSymbol.value
    if (validDexes.length > 0) {
      // If current selections are invalid for this symbol, or empty, update them
      if (!selectedLongDex.value || !validDexes.includes(selectedLongDex.value)) {
         selectedLongDex.value = validDexes[0]
      }
      
      if (!selectedShortDex.value || !validDexes.includes(selectedShortDex.value)) {
         // Try to pick a different one for short if possible
         selectedShortDex.value = validDexes.length > 1 ? validDexes[1] : validDexes[0]
      }
    }

  } catch (err) {
    error.value = 'Error loading data'
    console.error(err)
  } finally {
    loading.value = false
  }
}

// ===== LIFECYCLE =====

let refreshInterval = null

onMounted(async () => {
  // Initialize from props/route params
  if (props.initialSymbol || route.query.symbol) {
    selectedSymbol.value = props.initialSymbol || route.query.symbol
  }
  if (props.initialLongDex || route.query.longDex) {
    selectedLongDex.value = props.initialLongDex || route.query.longDex
  }
  if (props.initialShortDex || route.query.shortDex) {
    selectedShortDex.value = props.initialShortDex || route.query.shortDex
  }
  
  await loadAvailableDexes()
  await fetchData()
  
  refreshInterval = setInterval(fetchData, 10000)
  document.addEventListener('click', closeDropdowns)
  document.addEventListener('touchend', closeDropdowns)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  document.removeEventListener('click', closeDropdowns)
  document.removeEventListener('touchend', closeDropdowns)
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header With Asset Selector -->
    <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <!-- Asset Icon & Name -->
        <div class="flex items-center gap-3">
          <div class="relative dropdown-container">
            <button 
              @click.stop="toggleSymbolDropdown"
              class="flex items-center gap-3 pl-2 pr-4 py-2 hover:bg-white/5 rounded-xl transition-colors group"
            >
              <img 
                v-if="!isImageFailed(selectedSymbol)"
                :key="selectedSymbol"
                :src="getFallbackIcon(selectedSymbol)" 
                class="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-neutral-800 shadow-lg"
                @error="(e) => handleImageError(selectedSymbol, e)"
              />
              <div
                v-else
                class="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-sm sm:text-base text-white font-bold shadow-lg"
              >
                {{ getBaseSymbol(selectedSymbol).charAt(0) }}
              </div>
              <div class="text-left">
                <div class="flex items-center gap-2">
                  <h1 class="text-xl sm:text-2xl font-bold text-white tracking-tight">
                    {{ selectedSymbol.replace(/-USD-PERP/i, '').replace(/-PERP/i, '') }}
                  </h1>
                  <svg class="w-4 h-4 text-neutral-500 group-hover:text-white transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </div>
                <!-- Mini Pill for Symbol -->
                <span class="text-xs font-mono text-neutral-500">{{ selectedSymbol }}</span>
              </div>
            </button>

            <!-- Asset Dropdown -->
            <div 
              v-if="showSymbolDropdown"
              class="absolute left-0 top-full mt-2 w-72 bg-[#0E0E0E] border border-white/10 rounded-xl shadow-2xl z-50 overflow-hidden ring-1 ring-white/5"
            >
              <div class="p-3 border-b border-white/5">
                <input 
                  ref="symbolSearchInputRef"
                  v-model="symbolSearchQuery"
                  type="text" 
                  placeholder="Search assets..." 
                  class="w-full bg-white/5 border border-white/5 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500/50 transition-colors"
                />
              </div>
              
              <!-- Pinned Assets -->
              <div class="p-2 grid grid-cols-4 gap-2 border-b border-white/5">
                <button 
                  v-for="item in pinnedSymbols"
                  :key="item.symbol"
                  @click="selectSymbol(item.symbol)"
                  class="flex flex-col items-center gap-1 p-2 rounded-lg hover:bg-white/5 transition-colors"
                >
                  <img 
                    v-if="!isImageFailed(item.symbol)"
                    :src="item.iconUrl" 
                    class="w-6 h-6 rounded-full"
                    @error="(e) => handleImageError(item.symbol, e)"
                  />
                  <div
                    v-else
                    class="w-6 h-6 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-[10px] text-white font-bold"
                  >
                    {{ getBaseSymbol(item.symbol).charAt(0) }}
                  </div>
                  <span class="text-[10px] font-medium text-neutral-400">{{ item.symbol.substring(0,3) }}</span>
                </button>
              </div>

              <!-- List -->
              <div class="max-h-64 overflow-y-auto">
                <button 
                  v-for="item in filteredOtherSymbols"
                  :key="item.symbol"
                  @click="selectSymbol(item.symbol)"
                  class="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 transition-colors text-left"
                >
                  <img 
                    v-if="!isImageFailed(item.symbol)"
                    :src="item.iconUrl" 
                    class="w-5 h-5 rounded-full bg-neutral-800"
                    @error="(e) => handleImageError(item.symbol, e)"
                  />
                  <div
                    v-else
                    class="w-5 h-5 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-[8px] text-white font-bold"
                  >
                    {{ getBaseSymbol(item.symbol).charAt(0) }}
                  </div>
                  <span class="text-sm text-neutral-300">{{ item.symbol.replace('-USD-PERP', '') }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Actions / Navigation -->
      <div class="flex items-center gap-3">
        <RouterLink 
          to="/arbitrage"
          class="flex items-center gap-2 px-4 py-2 rounded-lg text-sm text-neutral-400 font-medium hover:text-white transition-colors"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 17l-5-5m0 0l5-5m-5 5h12"/>
          </svg>
          Back to Arbitrage
        </RouterLink>
      </div>
    </div>

    <!-- Main Dashboard Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 items-stretch">
      
      <!-- LEFT COL: Chart -->
      <div class="lg:col-span-2 flex flex-col">
        <!-- Main Chart -->
        <div class="bg-[#0A0A0A] border border-white/5 rounded-2xl p-1 overflow-hidden flex-1 flex flex-col min-h-[450px]">
          <FundingRateChart :symbol="selectedSymbol" />
        </div>
      </div>

      <!-- RIGHT COL: Strategy & Analysis -->
      <div class="flex flex-col">
        
        <!-- STRATEGY CARD -->
        <div class="bg-[#0A0A0A] border border-white/5 rounded-2xl p-5 relative overflow-hidden h-full">
          <div class="absolute top-0 right-0 w-32 h-32 bg-indigo-500/5 rounded-full blur-3xl"></div>
          
          <h2 class="text-sm font-semibold text-neutral-400 uppercase tracking-wider mb-5">Arbitrage Strategy</h2>

          <!-- Configuration -->
          <div class="space-y-4 mb-6">
            <!-- Long Side -->
            <div class="dropdown-container relative bg-white/5 rounded-xl p-3 border border-white/5 hover:border-emerald-500/30 transition-colors group">
              <label class="text-[10px] uppercase text-emerald-400/80 font-bold tracking-wider mb-1 block">Long Position</label>
              <button 
                @click.stop="toggleLongDexDropdown"
                class="w-full flex items-center justify-between text-white"
              >
                <span class="font-medium">{{ selectedLongDex || 'Select Exchange' }}</span>
                <svg :class="['w-4 h-4 text-neutral-500 transition-transform', showLongDexDropdown ? 'rotate-180' : '']" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
              </button>
              
              <!-- Dropdown -->
              <div v-if="showLongDexDropdown" class="absolute left-0 right-0 top-full mt-2 bg-[#1A1A1A] border border-white/10 rounded-lg shadow-xl z-20 py-1">
                <button v-for="dex in availableDexesForSymbol" :key="dex" @click.stop="selectLongDex(dex)" :disabled="dex === selectedShortDex" class="w-full text-left px-4 py-2 hover:bg-white/5 text-sm text-neutral-300 disabled:opacity-30">{{ dex }}</button>
              </div>
            </div>

            <!-- Swap Btn -->
            <div class="flex justify-center -my-2 relative z-10">
              <button 
                @click="swapDexes"
                class="p-2 rounded-full bg-[#0A0A0A] border border-white/10 text-neutral-400 hover:text-white hover:border-indigo-500/50 transition-all hover:rotate-180"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/></svg>
              </button>
            </div>

            <!-- Short Side -->
            <div class="dropdown-container relative bg-white/5 rounded-xl p-3 border border-white/5 hover:border-red-500/30 transition-colors group">
              <label class="text-[10px] uppercase text-red-400/80 font-bold tracking-wider mb-1 block">Short Position</label>
              <button 
                @click.stop="toggleShortDexDropdown"
                class="w-full flex items-center justify-between text-white"
              >
                <span class="font-medium">{{ selectedShortDex || 'Select Exchange' }}</span>
                <svg :class="['w-4 h-4 text-neutral-500 transition-transform', showShortDexDropdown ? 'rotate-180' : '']" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
              </button>

              <!-- Dropdown -->
              <div v-if="showShortDexDropdown" class="absolute left-0 right-0 top-full mt-2 bg-[#1A1A1A] border border-white/10 rounded-lg shadow-xl z-20 py-1">
                <button v-for="dex in availableDexesForSymbol" :key="dex" @click.stop="selectShortDex(dex)" :disabled="dex === selectedLongDex" class="w-full text-left px-4 py-2 hover:bg-white/5 text-sm text-neutral-300 disabled:opacity-30">{{ dex }}</button>
              </div>
            </div>
          </div>

          <!-- RESULTS -->
          <div v-if="spreadData" class="space-y-3 pt-4 border-t border-white/5">
            <!-- Position Size Selector -->
            <div class="mb-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-[10px] text-neutral-500 uppercase tracking-wider">Position Size</span>
              </div>
              <div class="flex items-center gap-1 flex-wrap">
                <button
                  v-for="option in positionSizeOptions"
                  :key="option.value"
                  @click="positionSize = option.value"
                  :class="[
                    'px-2 py-1 rounded text-[10px] font-medium transition-colors',
                    positionSize === option.value
                      ? 'bg-white/10 text-white border border-white/20'
                      : 'text-neutral-500 hover:text-white hover:bg-white/5'
                  ]"
                >
                  {{ option.label }}
                </button>
                <!-- Custom Input with styled arrows -->
                <div class="relative flex items-center">
                  <span class="absolute left-2 top-1/2 -translate-y-1/2 text-[10px] text-neutral-500 z-10">$</span>
                  <input
                    type="number"
                    v-model.number="positionSize"
                    min="100"
                    max="10000000"
                    step="1000"
                    class="position-input w-24 pl-4 pr-8 py-1.5 rounded-lg text-[11px] font-medium bg-white/5 border border-white/10 text-white focus:outline-none focus:border-indigo-500/50 text-right appearance-none"
                    placeholder="Custom"
                  />
                  <!-- Custom Arrow Buttons -->
                  <div class="absolute right-0.5 top-1/2 -translate-y-1/2 flex flex-col gap-0.5">
                    <button
                      type="button"
                      @click="positionSize = Math.min(positionSize + 1000, 10000000)"
                      class="w-5 h-3 flex items-center justify-center rounded-t bg-white/5 hover:bg-indigo-500/30 border border-white/10 hover:border-indigo-500/50 transition-all group"
                    >
                      <svg class="w-2.5 h-2.5 text-neutral-500 group-hover:text-indigo-400 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 15l7-7 7 7"/>
                      </svg>
                    </button>
                    <button
                      type="button"
                      @click="positionSize = Math.max(positionSize - 1000, 100)"
                      class="w-5 h-3 flex items-center justify-center rounded-b bg-white/5 hover:bg-indigo-500/30 border border-white/10 hover:border-indigo-500/50 transition-all group"
                    >
                      <svg class="w-2.5 h-2.5 text-neutral-500 group-hover:text-indigo-400 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- Daily Return -->
            <div class="p-4 rounded-xl bg-white/5 border border-white/5">
              <div class="flex justify-between items-center mb-2">
                <span class="text-xs text-neutral-400">Daily Return</span>
              </div>
              <div class="flex items-baseline gap-2">
                <span class="text-2xl font-bold font-mono" :class="spreadData.dailyReturn > 0 ? 'text-emerald-400' : 'text-red-400'">
                  {{ spreadData.dailyReturn > 0 ? '+' : ''}}{{ (spreadData.dailyReturn).toFixed(4) }}%
                </span>
                <span class="text-sm text-neutral-500">
                  ≈ ${{ (spreadData.dailyReturn * positionSize / 100).toFixed(2) }}
                </span>
              </div>
            </div>

            <!-- Annual Return -->
            <div class="p-4 rounded-xl bg-gradient-to-br from-indigo-500/10 to-purple-500/10 border border-indigo-500/20">
              <div class="flex justify-between items-center mb-2">
                <span class="text-xs text-indigo-300">Annual Return</span>
                <span class="text-[10px] text-indigo-400/60 bg-indigo-500/10 px-2 py-0.5 rounded">projected</span>
              </div>
              <div class="flex items-baseline gap-2">
                <span class="text-3xl font-bold font-mono" :class="spreadData.annualReturn > 0 ? 'text-indigo-400' : 'text-red-400'">
                  {{ spreadData.annualReturn > 0 ? '+' : ''}}{{ (spreadData.annualReturn).toFixed(2) }}%
                </span>
                <span class="text-sm text-indigo-300/50">
                  ≈ ${{ (spreadData.annualReturn * positionSize / 100).toFixed(0) }}
                </span>
              </div>
            </div>
            
            <!-- Split Info -->
            <div class="flex items-start gap-2 text-[10px] text-neutral-500 bg-white/5 p-3 rounded-lg">
              <svg class="w-3 h-3 mt-0.5 text-neutral-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              <span>
                Split ${{ (positionSize / 2).toLocaleString() }} on <span class="text-emerald-400">{{ selectedLongDex }}</span> (Long) 
                + ${{ (positionSize / 2).toLocaleString() }} on <span class="text-red-400">{{ selectedShortDex }}</span> (Short)
              </span>
            </div>
          </div>
          
          <div v-else class="text-center py-8 text-neutral-500 text-sm">
            Select both exchanges to analyze spread
          </div>

        </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-card {
  /* Retaining basic glass class just in case global css relies on it, but overriding visuals inline */
}

/* Hide native number input arrows */
.position-input::-webkit-outer-spin-button,
.position-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.position-input[type=number] {
  -moz-appearance: textfield;
}
</style>
