<script setup>
import { ref, onMounted } from 'vue'
import { fundingRateApi } from '../services/api'

const history = ref([])
const loading = ref(true)
const selectedSymbol = ref('BTC-USD-PERP')
const hours = ref(24)

const symbols = ['BTC-USD-PERP', 'ETH-USD-PERP', 'SOL-USD-PERP']

const fetchHistory = async () => {
  loading.value = true
  try {
    const response = await fundingRateApi.getHistory({
      dex: 'Paradex',
      symbol: selectedSymbol.value,
      hours: hours.value
    })
    history.value = response.data
  } catch (err) {
    console.error('Error fetching history:', err)
  } finally {
    loading.value = false
  }
}

const formatDate = (dateString) => {
  if (!dateString) return '--'
  const date = new Date(dateString)
  return date.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  fetchHistory()
})
</script>

<template>
  <!-- Filters -->
  <div class="glass-card rounded-3xl p-6">
    <h3 class="text-base font-medium text-white mb-6">Historical Data Filters</h3>
    
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <!-- Symbol Select -->
      <div>
        <label class="text-xs text-neutral-400 mb-2 block">Symbol</label>
        <div class="flex gap-2">
          <button 
            v-for="symbol in symbols" 
            :key="symbol"
            @click="selectedSymbol = symbol; fetchHistory()"
            :class="[
              'px-3 py-2 text-xs font-medium rounded-xl transition-all cursor-pointer',
              selectedSymbol === symbol 
                ? 'bg-white text-black' 
                : 'glass-card text-neutral-400 hover:text-white'
            ]"
          >
            {{ symbol.replace('-USD-PERP', '') }}
          </button>
        </div>
      </div>
      
      <!-- Time Range -->
      <div>
        <label class="text-xs text-neutral-400 mb-2 block">Time Range</label>
        <div class="flex gap-2">
          <button 
            v-for="h in [6, 24, 48, 168]" 
            :key="h"
            @click="hours = h; fetchHistory()"
            :class="[
              'px-3 py-2 text-xs font-medium rounded-xl transition-all cursor-pointer',
              hours === h 
                ? 'bg-indigo-500 text-white' 
                : 'glass-card text-neutral-400 hover:text-white'
            ]"
          >
            {{ h < 24 ? h + 'h' : (h / 24) + 'd' }}
          </button>
        </div>
      </div>
      
      <!-- Refresh -->
      <div class="flex items-end">
        <button 
          @click="fetchHistory"
          class="px-4 py-2 bg-white text-black text-xs font-semibold rounded-xl hover:bg-neutral-200 transition-colors flex items-center gap-2 cursor-pointer"
        >
          🔄 Refresh
        </button>
      </div>
    </div>
  </div>

  <!-- History Table -->
  <div class="glass-card rounded-3xl overflow-hidden">
    <div class="px-6 py-5 border-b border-white/5 flex items-center justify-between">
      <h3 class="text-base font-medium text-white">
        {{ selectedSymbol }} - Last {{ hours }}h
      </h3>
      <span class="text-xs text-neutral-500">{{ history.length }} records</span>
    </div>
    
    <div v-if="loading" class="p-12 flex items-center justify-center">
      <span class="animate-spin text-neutral-400">⏳</span>
    </div>
    
    <table v-else class="w-full">
      <thead class="bg-white/5">
        <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
          <th class="px-6 py-4 font-medium">Timestamp</th>
          <th class="px-6 py-4 font-medium">Rate</th>
          <th class="px-6 py-4 font-medium">8H Rate</th>
          <th class="px-6 py-4 font-medium">Annual</th>
          <th class="px-6 py-4 font-medium">Premium</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-white/5">
        <tr v-for="item in history" :key="item.id" class="hover:bg-white/5 transition-colors">
          <td class="px-6 py-4 text-xs text-neutral-300">{{ formatDate(item.timestamp) }}</td>
          <td class="px-6 py-4">
            <span :class="['text-xs font-medium', item.rate > 0 ? 'text-emerald-400' : 'text-red-400']">
              {{ (item.rate * 100).toFixed(8) }}%
            </span>
          </td>
          <td class="px-6 py-4 text-xs text-neutral-300">{{ item.rate8h?.toFixed(4) }}%</td>
          <td class="px-6 py-4">
            <span :class="['text-xs font-medium', item.rateAnnual > 0 ? 'text-emerald-400' : 'text-red-400']">
              {{ item.rateAnnual?.toFixed(2) }}%
            </span>
          </td>
          <td class="px-6 py-4 text-xs text-neutral-500">${{ item.fundingPremium?.toFixed(2) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
