<script setup>
import { ref, onMounted, computed } from 'vue'
import { fundingRateApi } from '../services/api'
import FundingRateChart from '../components/FundingRateChart.vue'

const fundingRates = ref([])
const loading = ref(true)
const error = ref(null)
const selectedSymbol = ref('BTC-USD-PERP')

const symbols = ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP', 'RESOLV-USD-PERP']

const latestRate = computed(() => {
  if (fundingRates.value.length === 0) return null
  return fundingRates.value.find(r => r.symbol === selectedSymbol.value) || fundingRates.value[0]
})

const fetchData = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await fundingRateApi.getAll()
    fundingRates.value = response.data
  } catch (err) {
    error.value = 'Erreur lors du chargement des données'
    console.error(err)
  } finally {
    loading.value = false
  }
}

const fetchLatestRate = async (symbol) => {
  selectedSymbol.value = symbol
  try {
    await fundingRateApi.fetchAndSave(symbol)
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
          <span class="text-xs text-white">{{ selectedSymbol }}</span>
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
    </div>
    <div class="flex flex-wrap gap-3">
      <button 
        v-for="symbol in symbols" 
        :key="symbol"
        @click="fetchLatestRate(symbol)"
        :class="[
          'px-4 py-2 text-xs font-medium rounded-xl transition-all cursor-pointer',
          selectedSymbol === symbol 
            ? 'bg-white text-black shadow-lg shadow-white/10' 
            : 'glass-card text-neutral-400 hover:text-white hover:bg-white/5'
        ]"
      >
        <div class="flex items-center gap-2">
            <!-- Icons -->
            <img v-if="symbol.startsWith('BTC')" src="https://cryptologos.cc/logos/bitcoin-btc-logo.png?v=025" class="w-4 h-4 rounded-full"/>
            <img v-else-if="symbol.startsWith('ETH')" src="https://cryptologos.cc/logos/ethereum-eth-logo.png?v=025" class="w-4 h-4 rounded-full"/>
            <img v-else-if="symbol.startsWith('SOL')" src="https://cryptologos.cc/logos/solana-sol-logo.png?v=025" class="w-4 h-4 rounded-full"/>
            <div v-else class="w-4 h-4 rounded-full bg-blue-500 flex items-center justify-center text-[6px] text-white">R</div>
            
            {{ symbol.replace('-USD-PERP', '') }}
        </div>
      </button>
    </div>
  </div>

  <!-- Analytics Chart -->
  <FundingRateChart :symbol="selectedSymbol" />
  
</template>
