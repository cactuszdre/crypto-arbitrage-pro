<script setup>
defineProps({
  rates: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const formatDate = (dateString) => {
  if (!dateString) return '--'
  const date = new Date(dateString)
  return date.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatRate = (rate) => {
  if (rate === null || rate === undefined) return '--'
  return (rate * 100).toFixed(8) + '%'
}
</script>

<template>
  <div class="glass-card rounded-3xl overflow-hidden">
    <div class="px-6 py-5 border-b border-white/5 flex items-center justify-between">
      <h3 class="text-base font-medium text-white">Funding Rates History</h3>
      <span class="text-xs text-neutral-500">{{ rates.length }} entries</span>
    </div>
    
    <!-- Loading State -->
    <div v-if="loading" class="p-12 flex items-center justify-center">
      <div class="flex items-center gap-3 text-neutral-400">
        <span class="animate-spin">⏳</span>
        <span class="text-sm">Loading data...</span>
      </div>
    </div>
    
    <!-- Empty State -->
    <div v-else-if="rates.length === 0" class="p-12 text-center">
      <span class="text-neutral-600 mb-4 text-5xl">📭</span>
      <p class="text-neutral-500 text-sm">No funding rate data available</p>
      <p class="text-neutral-600 text-xs mt-1">Click on a symbol to fetch data</p>
    </div>
    
    <!-- Table -->
    <table v-else class="w-full">
      <thead class="bg-white/5">
        <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
          <th class="px-6 py-4 font-medium">DEX</th>
          <th class="px-6 py-4 font-medium">Symbol</th>
          <th class="px-6 py-4 font-medium">Rate</th>
          <th class="px-6 py-4 font-medium">8H Rate</th>
          <th class="px-6 py-4 font-medium">Annual</th>
          <th class="px-6 py-4 font-medium">Direction</th>
          <th class="px-6 py-4 font-medium">Timestamp</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-white/5">
        <tr 
          v-for="rate in rates" 
          :key="rate.id" 
          class="group hover:bg-white/5 transition-colors"
        >
          <td class="px-6 py-4">
            <div class="flex items-center gap-2">
              <div class="w-6 h-6 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white text-[10px] font-bold">
                P
              </div>
              <span class="text-xs font-medium text-neutral-300 group-hover:text-white">{{ rate.dexName }}</span>
            </div>
          </td>
          <td class="px-6 py-4">
            <span class="text-xs font-medium text-white">{{ rate.symbol }}</span>
          </td>
          <td class="px-6 py-4">
            <span 
              :class="[
                'text-xs font-medium',
                rate.rate > 0 ? 'text-emerald-400' : 'text-red-400'
              ]"
            >
              {{ formatRate(rate.rate) }}
            </span>
          </td>
          <td class="px-6 py-4">
            <span class="text-xs text-neutral-300">{{ rate.rate8h?.toFixed(4) }}%</span>
          </td>
          <td class="px-6 py-4">
            <span 
              :class="[
                'text-xs font-medium',
                rate.rateAnnual > 0 ? 'text-emerald-400' : 'text-red-400'
              ]"
            >
              {{ rate.rateAnnual?.toFixed(2) }}%
            </span>
          </td>
          <td class="px-6 py-4">
            <span 
              :class="[
                'text-[10px] font-medium px-2 py-1 rounded-full',
                rate.direction === 'LONGS_PAY' 
                  ? 'bg-emerald-500/10 text-emerald-400' 
                  : 'bg-red-500/10 text-red-400'
              ]"
            >
              {{ rate.direction === 'LONGS_PAY' ? 'Longs Pay' : 'Shorts Pay' }}
            </span>
          </td>
          <td class="px-6 py-4">
            <span class="text-xs text-neutral-500">{{ formatDate(rate.timestamp) }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
