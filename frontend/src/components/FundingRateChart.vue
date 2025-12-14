<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { fundingRateApi, chartApi, dexApi } from '../services/api'
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
const isChangingMetric = ref(false) // Loading state for metric changes
const isChangingDex = ref(false) // Loading state for DEX changes
const isChangingSymbol = ref(false) // Loading state for symbol changes
const usingPreAggregated = ref(false) // Track if using pre-aggregated data

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

// Dynamic DEX config loaded from API
const availableDexes = ref([]) // List of DEX names
const dexConfigs = ref({}) // Map of dexName -> { name, color }
const dexes = computed(() => ['All', ...availableDexes.value])

// Get color for a DEX (from API or fallback)
const getDexColor = (dexName) => {
  return dexConfigs.value[dexName]?.color || '#6366F1'
}

// Load DEX configuration from API
const loadDexConfigs = async () => {
  try {
    const response = await dexApi.getAll()
    const configs = {}
    for (const dex of response.data) {
      configs[dex.name] = {
        name: dex.name,
        color: dex.color || '#6366F1'
      }
    }
    dexConfigs.value = configs
    availableDexes.value = response.data.map(d => d.name).sort()
  } catch (error) {
    console.error('Failed to load DEX configs:', error)
    // Fallback
    dexConfigs.value = {
      'Paradex': { name: 'Paradex', color: '#10B981' },
      'Lighter': { name: 'Lighter', color: '#6366F1' },
      'Hyperliquid': { name: 'Hyperliquid', color: '#F59E0B' }
    }
    availableDexes.value = ['Hyperliquid', 'Lighter', 'Paradex']
  }
}

// Aggregate data by 30-minute intervals - takes the latest value for each 30-min bucket
// Used as fallback when pre-aggregated data isn't available
const aggregateByInterval = (data) => {
  const buckets = new Map()
  const INTERVAL_MINUTES = 10

  for (const item of data) {
    const date = new Date(item.timestamp)
    // Calculate which 30-minute bucket this falls into (0 for 0-29, 1 for 30-59)
    const intervalBucket = Math.floor(date.getMinutes() / INTERVAL_MINUTES)
    // Create a key for the 30-minute bucket (year-month-day-hour-interval)
    const intervalKey = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}-${date.getHours()}-${intervalBucket}`
    const bucketKey = `${item.dex}-${intervalKey}`

    // Keep the latest entry for each dex per 30-minute interval
    if (!buckets.has(bucketKey) || new Date(buckets.get(bucketKey).timestamp) < date) {
      // Normalize timestamp to the start of the 30-minute interval
      const normalizedMinute = intervalBucket * INTERVAL_MINUTES
      buckets.set(bucketKey, {
        ...item,
        timestamp: new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), normalizedMinute).toISOString()
      })
    }
  }

  return Array.from(buckets.values())
}

// Try to fetch from pre-aggregated API, fallback to raw data if needed
const fetchData = async (silent = false) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const data = []
    let usedPreAggregated = false

    // First, try the pre-aggregated 5-minute chart API
    try {
      if (selectedDex.value === 'All') {
        // Use comparison endpoint for all DEXs
        const response = await chartApi.getComparisonData(props.symbol, 24)
        if (response.data && Object.keys(response.data).length > 0) {
          // Transform comparison data to flat array
          for (const [dexName, dexData] of Object.entries(response.data)) {
            for (const item of dexData) {
              data.push({
                dex: dexName,
                timestamp: item.bucketTime,
                rate: item.avgRate,
                rate8h: item.avgRate8h,
                rateAnnual: item.avgRateAnnual
              })
            }
          }
          usedPreAggregated = true
        }
      } else {
        // Fetch for specific DEX
        const response = await chartApi.getChartData(props.symbol, selectedDex.value, 24)
        if (response.data && response.data.length > 0) {
          for (const item of response.data) {
            data.push({
              dex: item.dexName || selectedDex.value,
              timestamp: item.bucketTime,
              rate: item.avgRate,
              rate8h: item.avgRate8h,
              rateAnnual: item.avgRateAnnual
            })
          }
          usedPreAggregated = true
        }
      }
    } catch (e) {
      console.log('[Chart] Pre-aggregated API not available, falling back to raw data')
    }

    // Fallback to raw data if pre-aggregated didn't return data
    if (data.length === 0) {
      // Dynamically fetch for each available DEX
      for (const dexName of availableDexes.value) {
        if (selectedDex.value === 'All' || selectedDex.value === dexName) {
          try {
            const response = await fundingRateApi.getHistory({
              dex: dexName,
              symbol: props.symbol,
              hours: 24
            })
            data.push(...response.data.map(d => ({ ...d, dex: dexName })))
          } catch (e) {
            console.error(`No ${dexName} data`, e)
          }
        }
      }

      // Aggregate by 10-minute intervals when using raw data
      const aggregated = aggregateByInterval(data)
      historyData.value = aggregated.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
    } else {
      // Pre-aggregated data is already in 5-minute buckets, just sort
      historyData.value = data.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
    }

    usingPreAggregated.value = usedPreAggregated
    
    // Increment key to force chart re-render
    chartKey.value++
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
    isInitialLoad.value = false
  }
}

// Watch for symbol changes - show loading overlay
watch(() => props.symbol, async () => {
  isChangingSymbol.value = true
  await fetchData(false)
  isChangingSymbol.value = false
})

// Watch for DEX changes - show loading overlay
watch(() => selectedDex.value, async () => {
  isChangingDex.value = true
  await fetchData(false)
  isChangingDex.value = false
})

// Initial fetch
onMounted(async () => {
  await loadDexConfigs() // Load DEX configs first
  fetchData(false)
})

// Watch for metric changes - show brief loading indicator
watch(() => selectedMetric.value, async () => {
  isChangingMetric.value = true
  // Small delay to show loading feedback
  await new Promise(resolve => setTimeout(resolve, 150))
  chartKey.value++
  isChangingMetric.value = false
})

// Auto-refresh every 10 seconds (silent - no loading spinner)
let refreshInterval = null
onMounted(() => {
  
  refreshInterval = setInterval(() => {
    fetchData(true) // Silent refresh
  }, 10000)
})

onUnmounted(() => {
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
            pointStyle: 'circle', // Points are circles (also affects tooltip indicators)
            pointRadius: 2,
            pointBackgroundColor: color, // Fill circle with dataset color (affects tooltip too)
            pointHoverRadius: 6,
            pointHoverBackgroundColor: color,
            pointHoverBorderColor: '#000',
            pointHoverBorderWidth: 2,
            borderWidth: 2.5,
            fill: false
        }
    }

    const datasets = []
    // Dynamically build datasets for each available DEX
    for (const dexName of availableDexes.value) {
      if (selectedDex.value === 'All' || selectedDex.value === dexName) {
        datasets.push(getDataset(dexName, getDexColor(dexName)))
      }
    }

    return { labels, datasets }
})

// Crosshair plugin for vertical line on hover
const crosshairPlugin = {
  id: 'crosshair',
  afterDraw: (chart) => {
    if (chart.tooltip?._active?.length) {
      const ctx = chart.ctx
      const activePoint = chart.tooltip._active[0]
      const x = activePoint.element.x
      const topY = chart.scales.y.top
      const bottomY = chart.scales.y.bottom

      ctx.save()
      ctx.beginPath()
      ctx.moveTo(x, topY)
      ctx.lineTo(x, bottomY)
      ctx.lineWidth = 1
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)'
      ctx.setLineDash([4, 4])
      ctx.stroke()
      ctx.restore()
    }
  }
}

// Register crosshair plugin
ChartJS.register(crosshairPlugin)

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
      backgroundColor: 'rgba(0, 0, 0, 0.85)',
      titleColor: '#fff',
      bodyColor: '#9CA3AF',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      borderWidth: 1,
      cornerRadius: 8,
      padding: window.innerWidth < 640 ? 8 : 12,
      displayColors: true,
      usePointStyle: true, // Use circles instead of squares for color indicators
      boxPadding: 4,
      titleFont: {
        size: window.innerWidth < 640 ? 11 : 12
      },
      bodyFont: {
        size: window.innerWidth < 640 ? 10 : 12
      },
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
        font: { size: window.innerWidth < 640 ? 9 : 10 },
        padding: window.innerWidth < 640 ? 4 : 8,
        maxTicksLimit: window.innerWidth < 640 ? 6 : 8
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
        font: { size: window.innerWidth < 640 ? 8 : 10 },
        maxTicksLimit: window.innerWidth < 640 ? 6 : 12,
        maxRotation: window.innerWidth < 640 ? 0 : 45
      },
      border: {
        display: false
      }
    }
  }
}))
</script>

<template>
  <div class="glass-card rounded-2xl p-4 sm:p-6 lg:p-8 min-h-[400px] sm:min-h-[500px] flex flex-col relative">
    <!-- Chart Header -->
    <div class="flex flex-col gap-3 sm:gap-4 mb-4 sm:mb-6 lg:mb-8">
      <div class="flex items-center justify-between">
        <h2 class="text-base sm:text-lg font-medium text-white tracking-tight">Funding Rate History</h2>

        <!-- Custom Legend (desktop) -->
        <div class="hidden sm:flex items-center gap-3 lg:gap-4 text-xs">
          <div 
            v-for="dexName in availableDexes" 
            :key="dexName"
            v-if="selectedDex === 'All' || selectedDex === dexName" 
            class="flex items-center gap-1.5 sm:gap-2"
          >
            <span 
              class="w-2 h-2 rounded-full" 
              :style="{ backgroundColor: getDexColor(dexName), boxShadow: `0 0 8px ${getDexColor(dexName)}99` }"
            ></span>
            <span class="text-neutral-400">{{ dexName }}</span>
          </div>
        </div>
      </div>

      <!-- Controls - Stack on mobile, inline on desktop -->
      <div class="flex flex-col sm:flex-row gap-2 sm:gap-0 sm:items-center sm:bg-black/40 sm:rounded-lg sm:p-1 sm:border sm:border-white/5 sm:w-fit">
        <!-- DEX Tabs -->
        <div class="flex items-center bg-black/40 sm:bg-transparent rounded-lg p-1 sm:p-0 border border-white/5 sm:border-0 overflow-x-auto">
          <button
            v-for="dex in dexes"
            :key="dex"
            @click="selectedDex = dex"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors border whitespace-nowrap flex-shrink-0',
              selectedDex === dex
                ? 'bg-emerald-600 text-white border-transparent shadow-lg shadow-emerald-500/25'
                : 'text-neutral-500 border-transparent hover:text-white'
            ]"
          >
            {{ dex }}
          </button>
        </div>

        <!-- Separator (desktop only) -->
        <div class="hidden sm:block w-px h-4 bg-white/10 mx-2"></div>

        <!-- Metric Tabs -->
        <div class="flex items-center bg-black/40 sm:bg-transparent rounded-lg p-1 sm:p-0 border border-white/5 sm:border-0 overflow-x-auto">
          <button
            v-for="metric in metrics"
            :key="metric.value"
            @click="selectedMetric = metric.value"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors border whitespace-nowrap flex-shrink-0',
              selectedMetric === metric.value
                ? 'bg-white/10 text-white border-white/10'
                : 'text-neutral-500 border-transparent hover:text-white'
            ]"
          >
            {{ metric.label }}
          </button>
        </div>
      </div>

      <!-- Custom Legend (mobile only) -->
      <div class="flex sm:hidden items-center gap-3 text-[10px]">
        <div 
          v-for="dexName in availableDexes" 
          :key="dexName"
          v-if="selectedDex === 'All' || selectedDex === dexName" 
          class="flex items-center gap-1.5"
        >
          <span 
            class="w-1.5 h-1.5 rounded-full" 
            :style="{ backgroundColor: getDexColor(dexName), boxShadow: `0 0 6px ${getDexColor(dexName)}99` }"
          ></span>
          <span class="text-neutral-400">{{ dexName }}</span>
        </div>
      </div>
    </div>

    <!-- Loading State (initial load or changing metric) -->
    <div v-if="loading && isInitialLoad" class="flex-1 flex items-center justify-center">
      <div class="flex flex-col items-center gap-3">
        <div class="w-6 h-6 sm:w-8 sm:h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
        <span class="text-xs sm:text-sm text-neutral-500">Loading chart data...</span>
      </div>
    </div>

    <!-- Chart (show after initial load, even during background refresh) -->
    <div v-else class="flex-1 relative w-full min-h-[250px] sm:min-h-[320px] lg:min-h-[360px]">
      <!-- Overlay loading for changes -->
      <div
        v-if="isChangingMetric || isChangingDex || isChangingSymbol"
        class="absolute inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-20 rounded-lg transition-opacity duration-150"
      >
        <div class="flex items-center gap-2 sm:gap-3">
          <div class="w-4 h-4 sm:w-5 sm:h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-xs sm:text-sm text-neutral-300">
            {{ isChangingSymbol ? 'Loading asset...' : isChangingDex ? 'Loading platform...' : 'Updating...' }}
          </span>
        </div>
      </div>
      <Line :key="chartKey" :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
