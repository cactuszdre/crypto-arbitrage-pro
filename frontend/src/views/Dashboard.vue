<script setup>
import { ref, onMounted, computed } from 'vue'
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

// Helper to get fallback icon URL from GitHub crypto-icons
const getFallbackIcon = (symbol) => {
  if (!symbol) return null
  const base = symbol.replace(/-USD-PERP$/i, '').replace(/-PERP$/i, '').toLowerCase()
  return `https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/32/color/${base}.png`
}

// Deduplicate symbols and ensure icon URLs
const processSymbols = (rawSymbols) => {
  const seen = new Map()
  for (const item of rawSymbols) {
    if (!seen.has(item.symbol)) {
      seen.set(item.symbol, {
        symbol: item.symbol,
        iconUrl: item.iconUrl || getFallbackIcon(item.symbol)
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <!-- Highlight Cards -->
  <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
    <!-- Card 1: Current Funding Rate -->
    <div class="glass-card p-6 rounded-3xl relative group overflow-hidden">
      <div class="absolute top-0 right-0 p-6 opacity-50 group-hover:opacity-100 transition-opacity">
        <span class="iconify text-white/10 group-hover:text-indigo-500/20 transform group-hover:scale-110 transition-all duration-500" data-icon="lucide:activity" data-width="64"></span>
      </div>
      <div class="relative z-10">
        <p class="text-xs font-medium text-neutral-400 uppercase tracking-wide">Current Funding Rate</p>
        <h2 class="text-3xl font-semibold text-white mt-2 tracking-tight">
          {{ latestRate ? (latestRate.rate * 100).toFixed(6) + '%' : '--' }}
        </h2>
        <div class="flex items-center gap-2 mt-4">
          <span 
            v-if="latestRate"
            :class="[
              'text-[10px] font-medium px-2 py-1 rounded-full border',
              latestRate.rate > 0 
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/10' 
                : 'bg-red-500/10 text-red-400 border-red-500/10'
            ]"
          >
            {{ latestRate.rate > 0 ? 'Longs pay Shorts' : 'Shorts pay Longs' }}
          </span>
        </div>
      </div>
    </div>

    <!-- Card 2: 8h Rate -->
    <div class="glass-card p-6 rounded-3xl relative group overflow-hidden">
      <div class="absolute top-0 right-0 p-6 opacity-50 group-hover:opacity-100 transition-opacity">
        <span class="iconify text-white/10 group-hover:text-blue-500/20 transform group-hover:scale-110 transition-all duration-500" data-icon="lucide:clock" data-width="64"></span>
      </div>
      <div class="relative z-10">
        <p class="text-xs font-medium text-neutral-400 uppercase tracking-wide">8H Funding Rate</p>
        <h2 class="text-3xl font-semibold text-white mt-2 tracking-tight">
          {{ latestRate ? latestRate.rate8h?.toFixed(4) + '%' : '--' }}
        </h2>
        <div class="flex items-center gap-2 mt-4">
          <span class="text-xs text-neutral-500">{{ latestRate?.dexName || 'Paradex' }}</span>
          <span class="iconify text-white" data-icon="lucide:arrow-right" data-width="12"></span>
          <!-- Dynamic Icon in Card -->
          <div class="flex items-center gap-1">
             <img 
                v-if="selectedSymbolObj?.iconUrl" 
                :src="selectedSymbolObj.iconUrl" 
                class="w-3 h-3 rounded-full"
                @error="$event.target.style.display='none'"
             />
             <span class="text-xs text-white">{{ selectedSymbol }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Card 3: Annual Rate -->
    <div class="glass-card p-6 rounded-3xl relative group overflow-hidden">
      <div class="absolute top-0 right-0 p-6 opacity-50 group-hover:opacity-100 transition-opacity">
        <span class="iconify text-white/10 group-hover:text-purple-500/20 transform group-hover:scale-110 transition-all duration-500" data-icon="lucide:trending-up" data-width="64"></span>
      </div>
      <div class="relative z-10">
        <p class="text-xs font-medium text-neutral-400 uppercase tracking-wide">Annualized Rate</p>
        <h2 class="text-3xl font-semibold text-white mt-2 tracking-tight">
          {{ latestRate ? latestRate.rateAnnual?.toFixed(2) + '%' : '--' }}
        </h2>
        <div class="flex items-center gap-2 mt-4">
          <span 
            :class="[
              'text-[10px] font-medium px-2 py-1 rounded-full border',
              latestRate && latestRate.rateAnnual > 0 
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/10' 
                : 'bg-red-500/10 text-red-400 border-red-500/10'
            ]"
          >
            {{ latestRate && latestRate.rateAnnual > 0 ? 'Bullish' : 'Bearish' }}
          </span>
        </div>
      </div>
    </div>
  </div>

  <!-- Symbol Selector -->
  <div class="glass-card rounded-3xl p-6 mb-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-base font-medium text-white">Select Asset to Monitor</h3>
      <span class="text-xs text-neutral-500">{{ symbols.length }} Assets Available</span>
    </div>
    
    <div class="flex flex-col gap-4">
      <!-- Major Assets (Pinned) -->
      <div class="flex flex-wrap gap-3">
         <button 
          v-for="item in pinnedSymbols" 
          :key="item.symbol"
          @click="fetchLatestRate(item.symbol)"
          :class="[
            'px-4 py-2 text-xs font-medium rounded-xl transition-all cursor-pointer border',
            selectedSymbol === item.symbol 
              ? 'bg-white text-black border-white shadow-lg shadow-white/10' 
              : 'glass-card text-neutral-300 border-white/5 hover:border-white/20 hover:text-white hover:bg-white/5'
          ]"
        >
          <div class="flex items-center gap-2">
              <img 
                :src="item.iconUrl || getFallbackIcon(item.symbol)" 
                class="w-4 h-4 rounded-full bg-neutral-800"
                @error="$event.target.style.display='none'" 
              />
              {{ item.symbol.replace('-USD-PERP', '') }}
          </div>
        </button>
      </div>

      <!-- Searchable Dropdown for others -->
      <div class="relative">
        <select 
          v-model="selectedSymbol"
          @change="fetchLatestRate(selectedSymbol)"
          class="w-full md:w-64 appearance-none bg-black/20 border border-white/10 rounded-xl px-4 py-2 text-sm text-white focus:outline-none focus:border-indigo-500/50 transition-colors cursor-pointer hover:bg-black/30"
        >
          <option value="" disabled>Select other asset...</option>
          <option 
            v-for="item in otherSymbols" 
            :key="item.symbol" 
            :value="item.symbol"
            class="bg-neutral-900 text-white"
          >
            {{ item.symbol.replace('-USD-PERP', '') }}
          </option>
        </select>
        <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-neutral-400 md:right-auto md:left-56">
          <span class="iconify" data-icon="lucide:chevron-down" data-width="16"></span>
        </div>
      </div>
    </div>
  </div>

  <!-- Analytics Chart -->
  <FundingRateChart :symbol="selectedSymbol" />
  
</template>
