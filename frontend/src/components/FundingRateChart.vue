<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { fundingRateApi } from '../services/api'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import { Line } from 'vue-chartjs'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

const props = defineProps({
  symbol: {
    type: String,
    required: true
  }
})

const loading = ref(true)
const isInitialLoad = ref(true)
const chartKey = ref(0) // Used to force chart re-render
const historyData = ref([])
const selectedDex = ref('All') // 'All', 'Paradex', 'Lighter', 'Hyperliquid'
const selectedMetric = ref('rateAnnual') // 'rate', 'rate8h', 'rateAnnual'

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

const dexes = ['All', 'Paradex', 'Lighter', 'Hyperliquid']

const dexColors = {
  'Paradex': '#10B981',    // Emerald
  'Lighter': '#6366F1',    // Indigo
  'Hyperliquid': '#F59E0B' // Amber
}

// Aggregate data by minute - takes the latest value for each minute bucket
const aggregateByMinute = (data) => {
  const buckets = new Map()
  
  for (const item of data) {
    const date = new Date(item.timestamp)
    // Create a key for the minute bucket (year-month-day-hour-minute)
    const minuteKey = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}-${date.getHours()}-${date.getMinutes()}`
    const bucketKey = `${item.dex}-${minuteKey}`
    
    // Keep the latest entry for each dex per minute
    if (!buckets.has(bucketKey) || new Date(buckets.get(bucketKey).timestamp) < date) {
      buckets.set(bucketKey, {
        ...item,
        // Normalize timestamp to the start of the minute
        timestamp: new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes()).toISOString()
      })
    }
  }
  
  return Array.from(buckets.values())
}

// Fetch data with optional silent mode (no loading spinner)
const fetchData = async (silent = false) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const data = []
    
    // Fetch for Paradex
    if (selectedDex.value === 'All' || selectedDex.value === 'Paradex') {
      try {
        const pResponse = await fundingRateApi.getHistory({
          dex: 'Paradex',
          symbol: props.symbol,
          hours: 24
        })
        data.push(...pResponse.data.map(d => ({ ...d, dex: 'Paradex' })))
      } catch (e) {
        console.error("No Paradex data", e)
      }
    }

    // Fetch for Lighter
    if (selectedDex.value === 'All' || selectedDex.value === 'Lighter') {
       try {
        const lResponse = await fundingRateApi.getHistory({
          dex: 'Lighter',
          symbol: props.symbol,
          hours: 24
        })
        data.push(...lResponse.data.map(d => ({ ...d, dex: 'Lighter' })))
       } catch (e) {
         console.error("No Lighter data", e)
       }
    }

    // Fetch for Hyperliquid
    if (selectedDex.value === 'All' || selectedDex.value === 'Hyperliquid') {
       try {
        const hResponse = await fundingRateApi.getHistory({
          dex: 'Hyperliquid',
          symbol: props.symbol,
          hours: 24
        })
        data.push(...hResponse.data.map(d => ({ ...d, dex: 'Hyperliquid' })))
       } catch (e) {
         console.error("No Hyperliquid data", e)
       }
    }

    // Aggregate by minute and sort by timestamp asc
    const aggregated = aggregateByMinute(data)
    historyData.value = aggregated.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
    
    // Increment key to force chart re-render
    chartKey.value++
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
    isInitialLoad.value = false
  }
}

// Watch for prop/selection changes - show loading on these
watch(() => [props.symbol, selectedDex.value], () => fetchData(false), { immediate: true })

// Auto-refresh every 10 seconds (silent - no loading spinner)
let refreshInterval = null
onMounted(() => {
  console.log('[Chart] Setting up auto-refresh interval (10s)')
  refreshInterval = setInterval(() => {
    console.log('[Chart] Auto-refreshing data...')
    fetchData(true) // Silent refresh
  }, 10000)
})

onUnmounted(() => {
  console.log('[Chart] Cleaning up refresh interval')
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
})

// Unified Timeline with aggregated data
const unifiedChartData = computed(() => {
    // 1. Collect all unique timestamps, sorted
    const allTimestamps = [...new Set(historyData.value.map(d => d.timestamp))].sort()
    
    // 2. Create labels (show only hour:minute)
    const labels = allTimestamps.map(t => new Date(t).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}))
    
    // 3. Map data to these timestamps
    const getDataset = (dexName, color) => {
        const metricsData = allTimestamps.map(t => {
            const entry = historyData.value.find(d => d.dex === dexName && d.timestamp === t)
            if (!entry) return null
            let val = entry[selectedMetric.value]
            if (selectedMetric.value === 'rate') val *= 100 // Convert rate to %
            return val
        })
        
        return {
            label: dexName,
            borderColor: color,
            backgroundColor: 'transparent',
            data: metricsData,
            tension: 0.4,
            spanGaps: true,
            pointRadius: 2,
            pointHoverRadius: 6,
            pointHoverBackgroundColor: color,
            pointHoverBorderColor: '#000',
            pointHoverBorderWidth: 2,
            borderWidth: 2.5,
            fill: false
        }
    }
    
    const datasets = []
    if (selectedDex.value === 'All' || selectedDex.value === 'Paradex') {
        datasets.push(getDataset('Paradex', dexColors['Paradex']))
    }
    if (selectedDex.value === 'All' || selectedDex.value === 'Lighter') {
        datasets.push(getDataset('Lighter', dexColors['Lighter']))
    }
    if (selectedDex.value === 'All' || selectedDex.value === 'Hyperliquid') {
        datasets.push(getDataset('Hyperliquid', dexColors['Hyperliquid']))
    }
    
    return { labels, datasets }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: {
    duration: isInitialLoad.value ? 750 : 0 // Animation only on first load
  },
  interaction: {
    mode: 'index',
    intersect: false
  },
  plugins: {
    legend: {
      display: false 
    },
    tooltip: {
      backgroundColor: 'rgba(0, 0, 0, 0.8)',
      titleColor: '#fff',
      bodyColor: '#9CA3AF',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      borderWidth: 1,
      cornerRadius: 8,
      padding: 12,
      displayColors: true,
      boxPadding: 4,
      animation: false, // No tooltip animation for snappy feel
      callbacks: {
        label: function(context) {
          let label = context.dataset.label || ''
          if (label) {
            label += ': '
          }
          if (context.parsed.y !== null) {
            // Use more decimals for 1H rate (rate is very small)
            const decimals = selectedMetric.value === 'rate' ? 6 : 
                            selectedMetric.value === 'rate8h' ? 4 : 2
            label += context.parsed.y.toFixed(decimals) + '%'
          }
          return label
        }
      }
    }
  },
  scales: {
    y: {
      grid: { 
        color: 'rgba(255, 255, 255, 0.05)',
        drawBorder: false
      },
      ticks: { 
        color: '#6B7280',
        font: { size: 10 },
        padding: 8
      },
      border: {
        display: false
      }
    },
    x: {
      grid: { 
        display: false 
      },
      ticks: { 
        color: '#6B7280',
        font: { size: 10 },
        maxTicksLimit: 12,
        maxRotation: 45
      },
      border: {
        display: false
      }
    }
  }
}))
</script>

<template>
  <div class="glass-card rounded-2xl p-6 md:p-8 min-h-[500px] flex flex-col relative">
    <!-- Chart Header -->
    <div class="flex flex-col md:flex-row md:items-center justify-between mb-8 gap-4">
      <h2 class="text-lg font-medium text-white tracking-tight">Funding Rate History</h2>
      
      <!-- Controls -->
      <div class="flex items-center gap-4 bg-black/40 rounded-lg p-1 border border-white/5">
        <!-- DEX Tabs -->
        <div class="flex items-center">
          <button 
            v-for="dex in dexes" 
            :key="dex"
            @click="selectedDex = dex"
            :class="[
              'px-3 py-1.5 rounded-md text-xs font-medium transition-colors border',
              selectedDex === dex 
                ? 'bg-indigo-600 text-white border-transparent shadow-lg shadow-indigo-500/25' 
                : 'text-neutral-500 border-transparent hover:text-white'
            ]"
          >
            {{ dex }}
          </button>
        </div>
        
        <!-- Separator -->
        <div class="w-px h-4 bg-white/10"></div>
        
        <!-- Metric Tabs -->
        <div class="flex items-center">
          <button 
            v-for="metric in metrics" 
            :key="metric.value"
            @click="selectedMetric = metric.value"
            :class="[
              'px-3 py-1.5 rounded-md text-xs font-medium transition-colors border',
              selectedMetric === metric.value 
                ? 'bg-white/10 text-white border-white/10' 
                : 'text-neutral-500 border-transparent hover:text-white'
            ]"
          >
            {{ metric.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Custom Legend -->
    <div class="absolute top-20 md:top-24 right-6 md:right-8 flex items-center gap-4 text-xs z-10">
      <div v-if="selectedDex === 'All' || selectedDex === 'Paradex'" class="flex items-center gap-2">
        <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]"></span>
        <span class="text-neutral-400">Paradex</span>
      </div>
      <div v-if="selectedDex === 'All' || selectedDex === 'Lighter'" class="flex items-center gap-2">
        <span class="w-2 h-2 rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.6)]"></span>
        <span class="text-neutral-400">Lighter</span>
      </div>
      <div v-if="selectedDex === 'All' || selectedDex === 'Hyperliquid'" class="flex items-center gap-2">
        <span class="w-2 h-2 rounded-full bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.6)]"></span>
        <span class="text-neutral-400">Hyperliquid</span>
      </div>
    </div>

    <!-- Loading State (only on initial load) -->
    <div v-if="loading && isInitialLoad" class="flex-1 flex items-center justify-center">
      <div class="flex flex-col items-center gap-3">
        <div class="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
        <span class="text-sm text-neutral-500">Loading chart data...</span>
      </div>
    </div>

    <!-- Chart (show after initial load, even during background refresh) -->
    <div v-else class="flex-1 relative w-full min-h-[360px] mt-4">
      <Line :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
