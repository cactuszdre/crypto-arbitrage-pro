<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { fundingRateApi } from '../services/api'
import FundingRateChart from '../components/FundingRateChart.vue'

const fundingRates = ref([])
const loading = ref(true)
const error = ref(null)
const selectedSymbol = ref('BTC-USD-PERP')
const symbols = ref([]) // Now contains objects { symbol: '...', iconUrl: '...' }

// Helper to get selected symbol object
const selectedSymbolObj = computed(() => {
    return symbols.value.find(s => s.symbol === selectedSymbol.value)
})

const latestRate = computed(() => {
  if (fundingRates.value.length === 0) return null
  return fundingRates.value.find(r => r.symbol === selectedSymbol.value) || fundingRates.value[0]
})

const pinnedSymbols = computed(() => {
  return symbols.value.filter(s => ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP'].includes(s.symbol))
})

const otherSymbols = computed(() => {
  return symbols.value
    .filter(s => !['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP'].includes(s.symbol))
    .sort((a, b) => a.symbol.localeCompare(b.symbol))
})

// Crypto brand colors
const cryptoColors = {
  'BTC': '#F7931A',
  'ETH': '#627EEA', 
  'SOL': '#14F195'
}

const getCryptoColor = (symbol) => {
  const base = symbol?.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toUpperCase()
  return cryptoColors[base] || '#6366F1'
}

// Helper to get fallback icon URL from GitHub crypto-icons
const getFallbackIcon = (symbol) => {
  if (!symbol) return null
  const base = symbol.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toUpperCase()
  return `https://app.paradex.trade/static/market/${base}.svg`
}

// Deduplicate symbols and ensure icon URLs
const processSymbols = (rawSymbols) => {
  const seen = new Map()
  for (const item of rawSymbols) {
    if (!seen.has(item.symbol)) {
      seen.set(item.symbol, {
        symbol: item.symbol,
        iconUrl: getFallbackIcon(item.symbol)
      })
    } else if (!seen.get(item.symbol).iconUrl && item.iconUrl) {
      // Update if current has no icon but this one does
      seen.get(item.symbol).iconUrl = item.iconUrl
    }
  }
  return Array.from(seen.values())
}

const fetchData = async () => {
  loading.value = true
  error.value = null
  try {
    const [ratesResponse, symbolsResponse] = await Promise.all([
      fundingRateApi.getAll(),
      fundingRateApi.getAllSymbols()
    ])
    fundingRates.value = ratesResponse.data
    
    if (symbolsResponse.data && symbolsResponse.data.length > 0) {
      // Deduplicate and ensure icons
      symbols.value = processSymbols(symbolsResponse.data)
    }
    
    // Default safety
    const exists = symbols.value.some(s => s.symbol === selectedSymbol.value)
    if (!exists && symbols.value.length > 0) {
       selectedSymbol.value = symbols.value[0].symbol
    }

  } catch (err) {
    error.value = 'Erreur lors du chargement des données'
    console.error(err)
  } finally {
    loading.value = false
  }
}


const fetchLatestRate = async (symbolStr) => {
  selectedSymbol.value = symbolStr
  try {
    await fundingRateApi.fetchAndSave(symbolStr)
    await fetchData()
  } catch (err) {
    console.error('Erreur lors de la récupération:', err)
  }
}

// Dropdown state
const showDropdown = ref(false)

// Auto-refresh every 10 seconds
let refreshInterval = null

onMounted(() => {
  fetchData()
  refreshInterval = setInterval(() => {
    fetchData()
  }, 10000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<template>
  <!-- Metrics Grid -->
  <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
    <!-- Card 1: Current Funding Rate -->
    <div class="glass-card rounded-2xl p-6 relative overflow-hidden group">
      <div class="absolute right-0 top-0 w-32 h-32 bg-indigo-500/5 rounded-full blur-3xl group-hover:bg-indigo-500/10 transition-colors"></div>
      <div class="flex justify-between items-start mb-4">
        <h3 class="text-xs font-semibold uppercase tracking-wider text-neutral-500">Current Funding Rate</h3>
        <svg class="w-5 h-5 text-indigo-500 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
        </svg>
      </div>
      <div class="flex items-baseline gap-2">
        <span class="text-3xl font-semibold text-white tracking-tight">
          {{ latestRate ? (latestRate.rate * 100).toFixed(6) + '%' : '--' }}
        </span>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <span 
          v-if="latestRate"
          :class="[
            'px-2 py-1 rounded text-xs font-medium border',
            latestRate.rate > 0 
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' 
              : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
          ]"
        >
          {{ latestRate.rate > 0 ? 'Longs pay Shorts' : 'Shorts pay Longs' }}
        </span>
      </div>
    </div>

    <!-- Card 2: 8h Rate -->
    <div class="glass-card rounded-2xl p-6 relative overflow-hidden group">
      <div class="absolute right-0 top-0 w-32 h-32 bg-orange-500/5 rounded-full blur-3xl group-hover:bg-orange-500/10 transition-colors"></div>
      <div class="flex justify-between items-start mb-4">
        <h3 class="text-xs font-semibold uppercase tracking-wider text-neutral-500">8H Funding Rate</h3>
        <svg class="w-5 h-5 text-orange-500 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
      </div>
      <div class="flex items-baseline gap-2">
        <span class="text-3xl font-semibold text-white tracking-tight">
          {{ latestRate ? latestRate.rate8h?.toFixed(4) + '%' : '--' }}
        </span>
      </div>
      <div class="mt-4 flex items-center gap-2 text-xs text-neutral-500">
        <span>{{ latestRate?.dexName || 'Paradex' }}</span>
        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"/>
        </svg>
        <span class="text-orange-300 flex items-center gap-1">
          <span class="w-1.5 h-1.5 rounded-full bg-orange-400"></span>
          {{ selectedSymbol }}
        </span>
      </div>
    </div>

    <!-- Card 3: Annual Rate -->
    <div class="glass-card rounded-2xl p-6 relative overflow-hidden group">
      <div class="absolute right-0 top-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-3xl group-hover:bg-emerald-500/10 transition-colors"></div>
      <div class="flex justify-between items-start mb-4">
        <h3 class="text-xs font-semibold uppercase tracking-wider text-neutral-500">Annualized Rate</h3>
        <svg class="w-5 h-5 text-emerald-500 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>
        </svg>
      </div>
      <div class="flex items-baseline gap-2">
        <span class="text-3xl font-semibold text-white tracking-tight">
          {{ latestRate ? latestRate.rateAnnual?.toFixed(2) + '%' : '--' }}
        </span>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <span 
          :class="[
            'px-2 py-1 rounded text-xs font-medium border',
            latestRate && latestRate.rateAnnual > 0 
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' 
              : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
          ]"
        >
          {{ latestRate && latestRate.rateAnnual > 0 ? 'Bullish' : 'Bearish' }}
        </span>
      </div>
    </div>
  </div>

  <!-- Control Bar -->
  <div class="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-8">
    <div class="space-y-3">
      <h2 class="text-lg font-medium text-white tracking-tight">Select Asset to Monitor</h2>
      <div class="flex items-center gap-3 flex-wrap">
        <!-- Pinned Assets (Pills) -->
        <button 
          v-for="item in pinnedSymbols" 
          :key="item.symbol"
          @click="fetchLatestRate(item.symbol)"
          :class="[
            'flex items-center gap-2 px-4 py-2 rounded-full font-medium text-sm transition-all',
            selectedSymbol === item.symbol 
              ? 'bg-white text-black shadow-[0_0_15px_rgba(255,255,255,0.2)] hover:scale-105' 
              : 'bg-white/5 border border-white/5 text-neutral-400 hover:bg-white/10 hover:text-white hover:border-white/20'
          ]"
        >
          <img 
            :src="item.iconUrl || getFallbackIcon(item.symbol)" 
            class="w-5 h-5 rounded-full bg-neutral-800"
            @error="$event.target.style.display='none'; $event.target.nextElementSibling.style.display='flex'"
          />
          <span 
            class="w-5 h-5 rounded-full flex items-center justify-center text-[8px] text-white font-bold hidden"
            :style="{ backgroundColor: getCryptoColor(item.symbol) }"
          >
            {{ item.symbol.charAt(0) }}
          </span>
          {{ item.symbol.replace('-USD-PERP', '') }}
        </button>

        <!-- Separator -->
        <div class="w-px h-6 bg-white/10 mx-1 hidden md:block"></div>

        <!-- Dropdown for other assets -->
        <div class="relative">
          <button 
            @click="showDropdown = !showDropdown"
            class="flex items-center justify-between w-40 px-3 py-2 rounded-lg bg-neutral-900/50 border border-white/10 text-sm text-neutral-400 hover:border-white/20 hover:text-white transition-colors"
          >
            <span>Select Asset</span>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
            </svg>
          </button>
          
          <!-- Dropdown Panel -->
          <div 
            v-if="showDropdown" 
            class="absolute left-0 top-full mt-2 w-56 max-h-64 overflow-y-auto bg-neutral-900 border border-white/10 rounded-xl shadow-xl z-50"
          >
            <button 
              v-for="item in otherSymbols" 
              :key="item.symbol"
              @click="fetchLatestRate(item.symbol); showDropdown = false"
              :class="[
                'w-full text-left px-4 py-2.5 text-sm flex items-center gap-3 transition-colors',
                selectedSymbol === item.symbol 
                  ? 'bg-indigo-500/20 text-white' 
                  : 'text-neutral-400 hover:bg-white/5 hover:text-white'
              ]"
            >
              <img 
                :src="item.iconUrl || getFallbackIcon(item.symbol)" 
                class="w-5 h-5 rounded-full bg-neutral-800"
                @error="$event.target.style.display='none'"
              />
              {{ item.symbol.replace('-USD-PERP', '') }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <div class="flex items-end">
      <p class="text-xs text-neutral-600 font-mono">{{ symbols.length }} Assets Available</p>
    </div>
  </div>

  <!-- Analytics Chart -->
  <FundingRateChart :symbol="selectedSymbol" />
  
</template>
