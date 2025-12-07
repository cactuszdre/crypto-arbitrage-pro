<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { arbitrageApi } from '../services/api'

const opportunities = ref([])
const currentPage = ref(1)
const itemsPerPage = 10
const failedImages = ref(new Set())
const searchQuery = ref('')

// Sorting state
const sortColumn = ref('projectedDailyReturn') // default sort by daily return
const sortDirection = ref('desc') // 'asc' or 'desc'

// Time period filter
const selectedPeriod = ref('current') // 'current', '1d', '7d', '30d'
const periodOptions = [
  { value: 'current', label: 'Current' },
  { value: '1d', label: '1 Day Avg' },
  { value: '7d', label: '7 Days Avg' },
  { value: '30d', label: '30 Days Avg' }
]

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

// Get sort icon for column
const getSortIcon = (column) => {
  if (sortColumn.value !== column) return '↕️'
  return sortDirection.value === 'asc' ? '↑' : '↓'
}

// Filter opportunities based on search query
const filteredOpportunities = computed(() => {
  let result = opportunities.value
  
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
const getCryptoIcon = (symbol) => {
  const base = getBaseSymbol(symbol)
  // Primary: CoinGecko assets via jsdelivr CDN (more reliable)
  return `https://assets.coingecko.com/coins/images/lookup?symbols=${base}`
}

// Fallback icon using crypto-icons (GitHub hosted, very reliable)
const getFallbackIcon = (symbol) => {
  const base = getBaseSymbol(symbol)
  return `https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/32/color/${base}.png`
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

onMounted(async () => {
    try {
        const response = await arbitrageApi.getOpportunities()
        opportunities.value = response.data
    } catch (error) {
        console.error('Failed to fetch opportunities:', error)
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
          </div>
        </div>
        
        <!-- Empty State: No data at all -->
        <div v-if="opportunities.length === 0" class="p-12 text-center">
          <span class="text-neutral-600 mb-4 text-5xl">📭</span>
          <p class="text-neutral-500 text-sm">Pas assez de données pour le moment</p>
          <p class="text-neutral-600 text-xs mt-1">Attendez le prochain cycle de fetch ou ajoutez plus de DEX</p>
        </div>

        <!-- Empty State: No search results -->
        <div v-else-if="filteredOpportunities.length === 0" class="p-12 text-center">
          <span class="text-neutral-600 mb-4 text-5xl">🔍</span>
          <p class="text-neutral-500 text-sm">Aucun résultat pour "{{ searchQuery }}"</p>
          <p class="text-neutral-600 text-xs mt-1">Essayez un autre ticker ou nom de DEX</p>
          <button 
            @click="searchQuery = ''; handleSearch()"
            class="mt-4 px-4 py-2 text-xs font-medium text-indigo-400 border border-indigo-500/30 rounded-lg hover:bg-indigo-500/10 transition-colors"
          >
            Effacer la recherche
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
                  <span class="text-[8px]">{{ getSortIcon('symbol') }}</span>
                </span>
              </th>
              <th class="px-6 py-4 font-medium">Route</th>
              <th 
                @click="toggleSort('longRate')"
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Long Rate
                  <span class="text-[8px]">{{ getSortIcon('longRate') }}</span>
                </span>
              </th>
              <th 
                @click="toggleSort('shortRate')"
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Short Rate
                  <span class="text-[8px]">{{ getSortIcon('shortRate') }}</span>
                </span>
              </th>
              <th 
                @click="toggleSort('projectedDailyReturn')"
                class="px-6 py-4 font-medium cursor-pointer hover:text-white transition-colors select-none"
              >
                <span class="flex items-center gap-1">
                  Daily Return
                  <span class="text-[8px]">{{ getSortIcon('projectedDailyReturn') }}</span>
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
                      class="w-6 h-6 rounded-full border border-black grayscale group-hover:grayscale-0 transition-all bg-neutral-800"
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
                  <span class="text-emerald-400">Long {{ opp.longDex }}</span>
                  <span>→</span>
                  <span class="text-red-400">Short {{ opp.shortDex }}</span>
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
                <button class="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center text-neutral-400 hover:text-white hover:bg-white/10 transition-colors">
                  ▶️
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

      <!-- Strategy Explanation -->
      <div class="glass-card rounded-3xl p-6">
        <h3 class="text-base font-medium text-white mb-4">Funding Rate Arbitrage Strategy</h3>
        <div class="space-y-4 text-sm text-neutral-400">
          <p>
            <span class="text-white font-medium">Principe:</span> Profiter des différences de funding rates entre DEX.
          </p>
          <div class="p-4 bg-white/5 rounded-xl border border-white/5">
            <p class="text-xs font-mono">
              <span class="text-emerald-400">LONG</span> sur le DEX avec le funding le plus négatif<br>
              <span class="text-red-400">SHORT</span> sur l'autre DEX<br>
              <span class="text-indigo-400">PROFIT</span> = |rate1| + |rate2| (si les deux sont négatifs)
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Strategy Config Panel -->
    <div class="glass-card rounded-3xl p-6 flex flex-col justify-between">
      <div>
        <h3 class="text-base font-medium text-white mb-6">Execution Strategy</h3>
        
        <div class="space-y-8">
          <!-- Slider 1 -->
          <div class="group">
            <div class="flex justify-between mb-3">
              <span class="text-xs text-neutral-400 font-medium group-hover:text-white transition-colors">Risk Tolerance</span>
              <span class="text-xs text-white">Medium</span>
            </div>
            <input type="range" class="w-full" value="45">
          </div>

          <!-- Slider 2 -->
          <div class="group">
            <div class="flex justify-between mb-3">
              <span class="text-xs text-neutral-400 font-medium group-hover:text-white transition-colors">Profit Trigger</span>
              <span class="text-xs text-white">0.5%</span>
            </div>
            <input type="range" class="w-full" value="20">
          </div>

          <!-- Switch -->
          <div class="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center">
                ⚡
              </div>
              <div class="flex flex-col">
                <span class="text-xs font-medium text-white">Auto-Pilot</span>
                <span class="text-[10px] text-neutral-500">Execute under 50ms</span>
              </div>
            </div>
            <div class="w-10 h-6 bg-indigo-600 rounded-full relative cursor-pointer">
              <div class="absolute top-1 right-1 w-4 h-4 bg-white rounded-full shadow-sm"></div>
            </div>
          </div>
        </div>
      </div>
      
      <button class="mt-8 w-full py-3 bg-white text-black text-xs font-semibold rounded-xl hover:bg-neutral-200 transition-colors shadow-lg shadow-white/10 cursor-pointer">
        Calibrate Strategy
      </button>
    </div>
  </div>
</template>
