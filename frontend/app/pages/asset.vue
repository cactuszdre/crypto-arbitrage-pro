<script setup>
// Nuxt auto-imports: ref, computed, watch, onMounted, onUnmounted, nextTick, useRoute, useRouter
// Using Nuxt SSR data fetching

// SEO
useSeoMeta({
  title: 'Asset Details - CactusFund',
  description: 'Analyze funding rates across exchanges for any crypto asset. Historical charts, spread calculations, and arbitrage strategy builder.',
  ogTitle: 'Crypto Asset Funding Rate Analysis - CactusFund',
  ogDescription: 'Analyze funding rates across exchanges. Historical charts and arbitrage strategy builder.',
  ogUrl: 'https://cactusfund.app/asset',
})

useHead({
  link: [{ rel: 'canonical', href: 'https://cactusfund.app/asset' }]
})

// SSR Data fetching
const { data: rawRatesData, refresh: refreshRates } = await useFetch('/api/opportunites/rates', {
  key: 'overview-rates',
  default: () => []
})

const { data: symbolsData } = await useFetch('/api/funding-rates/symbols', {
  key: 'overview-symbols',
  default: () => []
})

const { data: dexListData } = await useFetch('/api/dex', {
  key: 'overview-dex',
  default: () => []
})

// Props from router (when navigating from Arbitrage table)
const props = defineProps({
  initialSymbol: { type: String, default: null },
  initialLongDex: { type: String, default: null },
  initialShortDex: { type: String, default: null }
})

const route = useRoute()
const router = useRouter()

// Computed data from SSR fetch
const rawRates = computed(() => rawRatesData.value || [])
const symbols = computed(() => symbolsData.value || [])
const availableDexes = computed(() => {
  if (!dexListData.value) return []
  return dexListData.value.map(d => d.name).sort()
})
const dexDataMap = computed(() => {
  if (!dexListData.value) return {}
  const map = {}
  for (const dex of dexListData.value) {
    map[dex.name] = dex
  }
  return map
})

// Other data
const fundingRates = ref([])
const loading = computed(() => !rawRatesData.value)
const error = ref(null)

// Selected values
const selectedSymbol = ref('BTC-USD-PERP')
const selectedLongDex = ref(null)
const selectedShortDex = ref(null)
const positionSize = ref(10000) // Total position size in $

// Chart yield data (from ArbitrageHistoryChart)
const chartYieldData = ref({
  average: 0,
  averageFromChart: 0,
  chartDataPoints: 0,
  actualDays: 0,
  totalReturn: 0,
  periodLabel: 'Live',
  periodDays: 1,
  dataPoints: 0,
  isLive: true
})

const handleAverageYieldUpdate = (data) => {
  chartYieldData.value = data
}

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
// Funding arbitrage: Long pays rate, Short receives rate
// Net yield = shortRate - longRate
const spreadData = computed(() => {
  if (!longDexRate.value || !shortDexRate.value) return null
  
  const longRate = longDexRate.value.rate8h || 0
  const shortRate = shortDexRate.value.rate8h || 0
  // Directional spread: what you receive (short) minus what you pay (long)
  const spread = shortRate - longRate
  const dailyReturn = spread * 3 // 3 funding periods per day
  const annualReturn = dailyReturn * 365
  
  return {
    longRate,
    shortRate,
    spread,
    dailyReturn,
    annualReturn,
    isPositive: spread > 0
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
    path: '/asset',
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

// Get DEX icon URL by name (uses SSR data)
const getDexIconUrl = (dexName) => {
  return dexDataMap.value[dexName]?.iconUrl || null
}

// Get DEX trade URL with the selected symbol
const getDexTradeUrl = (dexName) => {
  const dex = dexDataMap.value[dexName]
  if (!dex || !dex.tradeUrlPattern) return null
  
  // Get the base symbol (e.g., "btc" from "BTC-USD-PERP")
  const baseSymbol = getBaseSymbol(selectedSymbol.value)
  
  // Replace all placeholders in the URL pattern:
  // {symbol} = full symbol (e.g., BTC-USD-PERP)
  // {base} = lowercase base (e.g., btc)
  // {BASE} = uppercase base (e.g., BTC)
  return dex.tradeUrlPattern
    .replace('{symbol}', selectedSymbol.value)
    .replace('{base}', baseSymbol.toLowerCase())
    .replace('{BASE}', baseSymbol.toUpperCase())
}

// Refresh data using Nuxt's refresh functions
const refreshData = async () => {
  error.value = null
  try {
    await refreshRates()
    
    // Update symbol data from rawRates
    if (rawRates.value && rawRates.value.length > 0) {
      const symbolInfo = rawRates.value.find(s => s.symbol === selectedSymbol.value)
      if (symbolInfo) {
        // Auto-select DEXes if not set
        const validDexes = symbolInfo.rates.map(r => r.dex).filter(dex => availableDexes.value.includes(dex))
        
        if (!selectedLongDex.value || !validDexes.includes(selectedLongDex.value)) {
           selectedLongDex.value = validDexes[0]
        }
        
        if (!selectedShortDex.value || !validDexes.includes(selectedShortDex.value)) {
           selectedShortDex.value = validDexes.length > 1 ? validDexes[1] : validDexes[0]
        }
      }
    }
  } catch (err) {
    error.value = 'Error loading data'
    console.error(err)
  }
}

// Scroll state for liquid glass effect
const isScrolled = ref(false)
const handleScroll = (e) => {
  isScrolled.value = e.target.scrollTop > 20
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
  
  // DEX list is loaded via SSR, just init DEX selections based on data
  await refreshData()
  
  // Set up auto-refresh
  refreshInterval = setInterval(refreshData, 10000)
  document.addEventListener('click', closeDropdowns)
  document.addEventListener('touchend', closeDropdowns)
  
  // Set up scroll listener on the main content container
  await nextTick()
  const scrollContainer = document.getElementById('main-scroll-container')
  if (scrollContainer) {
    scrollContainer.addEventListener('scroll', handleScroll)
    // Check initial scroll position
    isScrolled.value = scrollContainer.scrollTop > 20
  }
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  document.removeEventListener('click', closeDropdowns)
  document.removeEventListener('touchend', closeDropdowns)
  
  const scrollContainer = document.getElementById('main-scroll-container')
  if (scrollContainer) {
    scrollContainer.removeEventListener('scroll', handleScroll)
  }
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header With Asset Selector (Sticky Pill) -->
    <div class="sticky top-0 z-40 flex justify-start pointer-events-none mb-4">
      <div class="pointer-events-auto">
        <div class="relative dropdown-container">
          <button 
            @click.stop="toggleSymbolDropdown"
            :class="[
              'flex items-center gap-3 pl-2 pr-4 py-2 rounded-2xl transition-all duration-300 ease-out group hover:bg-white/10 hover:border-white/20',
              isScrolled 
                ? 'bg-[#0A0A0A]/80 backdrop-blur-xl border border-white/10 shadow-xl' 
                : 'bg-transparent border border-transparent'
            ]"
          >
            <img 
              v-if="!isImageFailed(selectedSymbol)"
              :key="selectedSymbol"
              :src="getFallbackIcon(selectedSymbol)" 
              class="w-10 h-10 rounded-full bg-neutral-800 shadow-lg"
              @error="(e) => handleImageError(selectedSymbol, e)"
            />
            <div
              v-else
              class="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center text-sm sm:text-base text-white font-bold shadow-lg"
            >
              {{ getBaseSymbol(selectedSymbol).charAt(0) }}
            </div>
            <div class="text-left flex flex-col justify-center">
              <h1 class="text-xl sm:text-2xl font-bold text-white tracking-tight leading-none mb-0.5">
                {{ selectedSymbol.replace(/-USD-PERP/i, '').replace(/-PERP/i, '') }}
              </h1>
              <!-- Mini Pill for Symbol -->
              <span class="text-xs font-mono text-neutral-500 leading-none">{{ selectedSymbol }}</span>
            </div>
            <svg class="w-4 h-4 text-neutral-500 group-hover:text-white transition-colors ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
            </svg>
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
                      :src="getFallbackIcon(item.symbol)" 
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
                      :src="getFallbackIcon(item.symbol)" 
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

    <!-- Arbitrage Section (Connected Chart + Strategy) -->
    <div class="relative">
      <!-- Shared Background Glow -->
      <div class="absolute -inset-1 bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-indigo-500/10 rounded-3xl blur-xl opacity-50"></div>
      
      <div class="relative grid grid-cols-1 lg:grid-cols-3 gap-0 lg:gap-0 items-stretch">
        <!-- Arbitrage History Chart -->
        <div class="lg:col-span-2">
          <div class="bg-[#0A0A0A]/90 backdrop-blur-xl border border-white/5 lg:border-r-0 rounded-2xl lg:rounded-r-none p-1 overflow-hidden flex flex-col min-h-[450px] shadow-2xl h-full">
            <ArbitrageHistoryChart 
              :symbol="selectedSymbol" 
              :long-dex="selectedLongDex" 
              :short-dex="selectedShortDex" 
              @update:average-yield="handleAverageYieldUpdate"
            />
          </div>
        </div>

        <!-- Strategy Card -->
        <div class="lg:col-span-1">
          <div class="bg-[#0A0A0A]/90 backdrop-blur-xl border border-white/5 lg:border-l-0 rounded-2xl lg:rounded-l-none p-5 relative shadow-2xl h-full">
            <div class="absolute top-0 right-0 w-32 h-32 bg-indigo-500/10 rounded-full blur-3xl"></div>
            
            <h2 class="text-sm font-semibold text-neutral-400 uppercase tracking-wider mb-5 flex items-center gap-2">
              <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
              Arbitrage Strategy
            </h2>

            <!-- Configuration -->
            <div class="space-y-4 mb-6">
              <!-- Long Side -->
              <div 
                class="dropdown-container relative rounded-xl border border-white/5 hover:border-emerald-500/40 transition-all cursor-pointer group"
                :class="{ 'z-30': showLongDexDropdown }"
                @click.stop="toggleLongDexDropdown"
              >
                <div class="flex items-center gap-3 p-4 bg-gradient-to-r from-emerald-500/10 to-transparent rounded-xl">
                  <!-- DEX Icon -->
                  <div class="w-10 h-10 rounded-xl bg-white/10 flex items-center justify-center overflow-hidden flex-shrink-0">
                    <img 
                      v-if="getDexIconUrl(selectedLongDex)"
                      :src="getDexIconUrl(selectedLongDex)" 
                      :alt="selectedLongDex"
                      class="w-full h-full object-cover"
                    />
                    <span v-else class="text-lg font-bold text-emerald-400">{{ selectedLongDex?.charAt(0) || '?' }}</span>
                  </div>
                  <div class="flex-1 min-w-0">
                    <label class="text-[10px] uppercase text-emerald-400/80 font-bold tracking-wider flex items-center gap-1.5">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 shadow-[0_0_8px_#10b981]"></span>
                      Long Position
                    </label>
                    <span class="font-semibold text-lg text-white truncate flex items-center gap-1.5">
                      {{ selectedLongDex || 'Select Exchange' }}
                      <!-- External Link Icon -->
                      <a 
                        v-if="getDexTradeUrl(selectedLongDex)"
                        :href="getDexTradeUrl(selectedLongDex)"
                        target="_blank"
                        rel="noopener noreferrer"
                        @click.stop
                        class="inline-flex items-center hover:opacity-80 transition-opacity"
                        title="Open on exchange"
                      >
                        <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
                        </svg>
                      </a>
                    </span>
                  </div>
                  <svg :class="['w-5 h-5 text-neutral-500 transition-transform flex-shrink-0', showLongDexDropdown ? 'rotate-180' : '']"
 fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                </div>
                
                <!-- Dropdown -->
                <div v-if="showLongDexDropdown" class="absolute left-0 right-0 top-full mt-1 bg-[#1A1A1A] border border-white/10 rounded-lg shadow-xl z-20 py-1">
                  <button 
                    v-for="dex in availableDexesForSymbol" 
                    :key="dex" 
                    @click.stop="selectLongDex(dex)" 
                    :disabled="dex === selectedShortDex" 
                    class="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 text-sm text-neutral-300 disabled:opacity-30 transition-colors"
                  >
                    <img 
                      v-if="getDexIconUrl(dex)"
                      :src="getDexIconUrl(dex)" 
                      :alt="dex"
                      class="w-6 h-6 rounded-lg object-cover"
                    />
                    <div v-else class="w-6 h-6 rounded-lg bg-white/10 flex items-center justify-center text-xs font-bold text-neutral-400">{{ dex.charAt(0) }}</div>
                    {{ dex }}
                  </button>
                </div>
              </div>

              <!-- Swap Btn -->
              <div class="flex justify-center -my-3 relative z-10">
                <button 
                  @click="swapDexes"
                  class="p-2 rounded-full bg-[#0E0E0E] border border-white/10 text-neutral-400 hover:text-white hover:border-indigo-500/50 hover:bg-white/5 transition-all hover:rotate-180 hover:scale-110 shadow-lg"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/></svg>
                </button>
              </div>

              <!-- Short Side -->
              <div 
                class="dropdown-container relative rounded-xl border border-white/5 hover:border-red-500/40 transition-all cursor-pointer group"
                :class="{ 'z-30': showShortDexDropdown }"
                @click.stop="toggleShortDexDropdown"
              >
                <div class="flex items-center gap-3 p-4 bg-gradient-to-r from-red-500/10 to-transparent rounded-xl">
                  <!-- DEX Icon -->
                  <div class="w-10 h-10 rounded-xl bg-white/10 flex items-center justify-center overflow-hidden flex-shrink-0">
                    <img 
                      v-if="getDexIconUrl(selectedShortDex)"
                      :src="getDexIconUrl(selectedShortDex)" 
                      :alt="selectedShortDex"
                      class="w-full h-full object-cover"
                    />
                    <span v-else class="text-lg font-bold text-red-400">{{ selectedShortDex?.charAt(0) || '?' }}</span>
                  </div>
                  <div class="flex-1 min-w-0">
                    <label class="text-[10px] uppercase text-red-400/80 font-bold tracking-wider flex items-center gap-1.5">
                      <span class="w-1.5 h-1.5 rounded-full bg-red-500 shadow-[0_0_8px_#ef4444]"></span>
                      Short Position
                    </label>
                    <span class="font-semibold text-lg text-white truncate flex items-center gap-1.5">
                      {{ selectedShortDex || 'Select Exchange' }}
                      <!-- External Link Icon -->
                      <a 
                        v-if="getDexTradeUrl(selectedShortDex)"
                        :href="getDexTradeUrl(selectedShortDex)"
                        target="_blank"
                        rel="noopener noreferrer"
                        @click.stop
                        class="inline-flex items-center hover:opacity-80 transition-opacity"
                        title="Open on exchange"
                      >
                        <svg class="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
                        </svg>
                      </a>
                    </span>
                  </div>
                  <svg :class="['w-5 h-5 text-neutral-500 transition-transform flex-shrink-0', showShortDexDropdown ? 'rotate-180' : '']" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                </div>

                <!-- Dropdown -->
                <div v-if="showShortDexDropdown" class="absolute left-0 right-0 top-full mt-1 bg-[#1A1A1A] border border-white/10 rounded-lg shadow-xl z-20 py-1">
                  <button 
                    v-for="dex in availableDexesForSymbol" 
                    :key="dex" 
                    @click.stop="selectShortDex(dex)" 
                    :disabled="dex === selectedLongDex" 
                    class="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 text-sm text-neutral-300 disabled:opacity-30 transition-colors"
                  >
                    <img 
                      v-if="getDexIconUrl(dex)"
                      :src="getDexIconUrl(dex)" 
                      :alt="dex"
                      class="w-6 h-6 rounded-lg object-cover"
                    />
                    <div v-else class="w-6 h-6 rounded-lg bg-white/10 flex items-center justify-center text-xs font-bold text-neutral-400">{{ dex.charAt(0) }}</div>
                    {{ dex }}
                  </button>
                </div>
              </div>
            </div>

            <!-- RESULTS -->
            <div v-if="spreadData" class="space-y-3 pt-4 border-t border-white/5">
              <!-- Position Size Selector -->
              <div class="mb-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-[10px] text-neutral-500 uppercase tracking-wider font-semibold">Position Size</span>
                </div>
                <div class="flex items-center gap-1 flex-wrap">
                  <button
                    v-for="option in positionSizeOptions"
                    :key="option.value"
                    @click="positionSize = option.value"
                    :class="[
                      'px-2 py-1 rounded text-[10px] font-medium transition-colors border',
                      positionSize === option.value
                        ? 'bg-white/10 text-white border-white/20 shadow-sm'
                        : 'border-transparent text-neutral-500 hover:text-white hover:bg-white/5'
                    ]"
                  >
                    {{ option.label }}
                  </button>
                  <!-- Custom Input with styled arrows -->
                  <div class="relative flex items-center ml-auto">
                    <span class="absolute left-2 top-1/2 -translate-y-1/2 text-[10px] text-neutral-500 z-10">$</span>
                    <input
                      type="number"
                      v-model.number="positionSize"
                      min="100"
                      max="10000000"
                      step="1000"
                      class="position-input w-24 pl-4 pr-8 py-1.5 rounded-lg text-[11px] font-medium bg-white/5 border border-white/10 text-white focus:outline-none focus:border-indigo-500/50 text-right appearance-none transition-colors"
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
              
              <!-- Yield Comparison Cards -->
              <div class="grid grid-cols-2 gap-3">
                <!-- LEFT: Real-time (Live) / Total Return (Historical) -->
                <div class="p-4 rounded-xl bg-gradient-to-br from-emerald-500/10 to-teal-500/10 border border-emerald-500/20 relative group hover:border-emerald-500/40 transition-colors">
                  <div class="absolute top-0 right-0 w-20 h-20 bg-emerald-500/10 rounded-full blur-2xl -mr-8 -mt-8 opacity-50 group-hover:opacity-100 transition-opacity"></div>
                  
                  <!-- LIVE MODE: Real-time snapshot -->
                  <template v-if="chartYieldData.isLive">
                    <!-- Header -->
                    <div class="flex items-center justify-between mb-3 relative z-10">
                      <div class="flex items-center gap-1.5">
                        <span class="text-[10px] uppercase tracking-wider text-emerald-400 font-semibold">Real-time</span>
                        <div class="relative group/tooltip">
                          <svg class="w-3 h-3 text-emerald-400/50 hover:text-emerald-400 cursor-help transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                          <div class="absolute bottom-full left-0 mb-2 w-48 p-2 bg-neutral-900 border border-white/10 rounded-lg text-[10px] text-neutral-300 opacity-0 group-hover/tooltip:opacity-100 pointer-events-none transition-opacity z-50 shadow-xl">
                            Current funding rate snapshot in real-time
                          </div>
                        </div>
                      </div>
                      <span class="flex items-center gap-1 text-[9px] text-emerald-400 bg-emerald-500/20 px-1.5 py-0.5 rounded">
                        <span class="relative flex h-1.5 w-1.5">
                          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                          <span class="relative inline-flex rounded-full h-1.5 w-1.5 bg-emerald-500"></span>
                        </span>
                        Live
                      </span>
                    </div>
                    
                    <!-- Daily -->
                    <div class="mb-3 relative z-10">
                      <div class="text-[10px] text-neutral-500 mb-0.5">Daily</div>
                      <div class="flex items-baseline gap-1.5">
                        <span class="text-lg font-bold font-mono" :class="chartYieldData.average > 0 ? 'text-emerald-400' : 'text-red-400'">
                          {{ chartYieldData.average > 0 ? '+' : ''}}{{ (chartYieldData.average / 365).toFixed(4) }}%
                        </span>
                        <span class="text-[10px] text-neutral-500">
                          ${{ ((chartYieldData.average / 365) * positionSize / 100).toFixed(2) }}
                        </span>
                      </div>
                    </div>
                    
                    <!-- Annual -->
                    <div class="relative z-10 pt-2 border-t border-emerald-500/10">
                      <div class="text-[10px] text-neutral-500 mb-0.5">Annual</div>
                      <div class="flex items-baseline gap-1.5">
                        <span class="text-xl font-bold font-mono" :class="chartYieldData.average > 0 ? 'text-emerald-300' : 'text-red-400'">
                          {{ chartYieldData.average > 0 ? '+' : ''}}{{ chartYieldData.average.toFixed(2) }}%
                        </span>
                        <span class="text-[10px] text-neutral-500">
                          ${{ (chartYieldData.average * positionSize / 100).toFixed(0) }}
                        </span>
                      </div>
                    </div>
                  </template>
                  
                  <!-- HISTORICAL MODE: Total Return -->
                  <template v-else>
                    <!-- Header -->
                    <div class="flex items-center justify-between mb-3 relative z-10">
                      <div class="flex items-center gap-1.5">
                        <span class="text-[10px] uppercase tracking-wider text-emerald-400 font-semibold">Total Return</span>
                        <div class="relative group/tooltip">
                          <svg class="w-3 h-3 text-emerald-400/50 hover:text-emerald-400 cursor-help transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                          <div class="absolute bottom-full left-0 mb-2 w-52 p-2 bg-neutral-900 border border-white/10 rounded-lg text-[10px] text-neutral-300 opacity-0 group-hover/tooltip:opacity-100 pointer-events-none transition-opacity z-50 shadow-xl">
                            Cumulative yield if you had held this arbitrage position for {{ chartYieldData.actualDays }} days
                          </div>
                        </div>
                      </div>
                      <span class="text-[9px] text-emerald-300/80 bg-emerald-500/20 px-1.5 py-0.5 rounded">
                        {{ chartYieldData.actualDays }} days
                      </span>
                    </div>
                    
                    <!-- Cumulative Return - Main Display (Vertically Centered) -->
                    <div class="relative z-10 flex flex-col items-start justify-center flex-1 py-2">
                      <div class="text-[10px] text-neutral-500 mb-1">{{ chartYieldData.periodLabel }} Period</div>
                      <div class="flex items-baseline gap-2">
                        <span class="text-3xl font-bold font-mono" :class="chartYieldData.totalReturn > 0 ? 'text-emerald-400' : 'text-red-400'">
                          {{ chartYieldData.totalReturn > 0 ? '+' : ''}}{{ chartYieldData.totalReturn.toFixed(2) }}%
                        </span>
                      </div>
                      <span class="text-sm text-neutral-500 mt-1">
                        ${{ (chartYieldData.totalReturn * positionSize / 100).toFixed(0) }}
                      </span>
                    </div>
                  </template>
                </div>
                
                <!-- RIGHT: Today's Average (Live) / Period Average (Historical) -->
                <div class="p-4 rounded-xl bg-gradient-to-br from-cyan-500/10 to-blue-500/10 border border-cyan-500/20 relative group hover:border-cyan-500/40 transition-colors">
                  <div class="absolute top-0 right-0 w-20 h-20 bg-cyan-500/10 rounded-full blur-2xl -mr-8 -mt-8 opacity-50 group-hover:opacity-100 transition-opacity"></div>
                  
                  <!-- Header -->
                  <div class="flex items-center justify-between mb-3 relative z-10">
                    <div class="flex items-center gap-1.5">
                      <span class="text-[10px] uppercase tracking-wider text-cyan-400 font-semibold">
                        {{ chartYieldData.isLive ? "Today's Avg" : 'Period Avg' }}
                      </span>
                      <div class="relative group/tooltip">
                        <svg class="w-3 h-3 text-cyan-400/50 hover:text-cyan-400 cursor-help transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                        <div class="absolute bottom-full left-0 mb-2 w-52 p-2 bg-neutral-900 border border-white/10 rounded-lg text-[10px] text-neutral-300 opacity-0 group-hover/tooltip:opacity-100 pointer-events-none transition-opacity z-50 shadow-xl">
                          <span v-if="chartYieldData.isLive">Average yield calculated from all data collected today</span>
                          <span v-else>Average daily and annualized yield over the {{ chartYieldData.periodLabel }} period</span>
                        </div>
                      </div>
                    </div>
                    <span class="text-[9px] text-cyan-300/80 bg-cyan-500/20 px-1.5 py-0.5 rounded">
                      {{ chartYieldData.isLive ? 'Today' : chartYieldData.periodLabel }}
                    </span>
                  </div>
                  
                  <!-- Daily -->
                  <div class="mb-3 relative z-10">
                    <div class="text-[10px] text-neutral-500 mb-0.5">Daily</div>
                    <div class="flex items-baseline gap-1.5">
                      <span class="text-lg font-bold font-mono" :class="chartYieldData.averageFromChart > 0 ? 'text-cyan-400' : 'text-red-400'">
                        {{ chartYieldData.averageFromChart > 0 ? '+' : ''}}{{ (chartYieldData.averageFromChart / 365).toFixed(4) }}%
                      </span>
                      <span class="text-[10px] text-neutral-500">
                        ${{ ((chartYieldData.averageFromChart / 365) * positionSize / 100).toFixed(2) }}
                      </span>
                    </div>
                  </div>
                  
                  <!-- Annual -->
                  <div class="relative z-10 pt-2 border-t border-cyan-500/10">
                    <div class="text-[10px] text-neutral-500 mb-0.5">Annual</div>
                    <div class="flex items-baseline gap-1.5">
                      <span class="text-xl font-bold font-mono" :class="chartYieldData.averageFromChart > 0 ? 'text-cyan-300' : 'text-red-400'">
                        {{ chartYieldData.averageFromChart > 0 ? '+' : ''}}{{ chartYieldData.averageFromChart.toFixed(2) }}%
                      </span>
                      <span class="text-[10px] text-neutral-500">
                        ${{ (chartYieldData.averageFromChart * positionSize / 100).toFixed(0) }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- Split Info -->
              <div class="flex items-start gap-2 text-[10px] text-neutral-500 bg-white/5 p-3 rounded-lg border border-white/5">
                <svg class="w-3 h-3 mt-0.5 text-neutral-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                <span>
                  Split ${{ (positionSize / 2).toLocaleString() }} on <span class="text-emerald-400">{{ selectedLongDex }}</span> (Long) 
                  + ${{ (positionSize / 2).toLocaleString() }} on <span class="text-red-400">{{ selectedShortDex }}</span> (Short)
                </span>
              </div>
            </div>
            
            <div v-else class="text-center py-12 text-neutral-500 text-sm bg-white/5 rounded-xl border border-white/5 border-dashed">
              Select both exchanges to analyze spread
            </div>

          </div>
        </div>
      </div>
    </div>

    <!-- Funding Rate History (Full Width) -->
    <div class="relative group mt-6">
      <div class="absolute -inset-0.5 bg-gradient-to-r from-indigo-500/20 to-purple-500/20 rounded-2xl blur opacity-30 group-hover:opacity-50 transition duration-1000"></div>
      <div class="relative bg-[#0A0A0A]/90 backdrop-blur-xl border border-white/5 rounded-2xl p-1 overflow-hidden flex flex-col min-h-[450px] shadow-2xl">
        <FundingRateChart :symbol="selectedSymbol" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Hide native number input arrows */
.position-input::-webkit-outer-spin-button,
.position-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  appearance: none;
  margin: 0;
}

.position-input[type=number] {
  -moz-appearance: textfield;
  appearance: textfield;
}
</style>
