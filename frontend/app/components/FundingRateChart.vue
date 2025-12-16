<script setup>
// Nuxt auto-imports: ref, computed, watch, onMounted, onUnmounted
import { fundingRateApi, chartApi, dexApi } from '~/services/api'
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
const selectedMetric = ref('rateAnnual') // 'rate', 'rate8h', 'rate24h', 'rateAnnual'
const selectedPeriod = ref(8) // Hours: 8 (Live), 24 (1D), 72 (3D), 168 (7D), 720 (30D), etc.
const isChangingMetric = ref(false) // Loading state for metric changes
const isChangingDex = ref(false) // Loading state for DEX changes
const isChangingSymbol = ref(false) // Loading state for symbol changes
const isChangingPeriod = ref(false)
const usingPreAggregated = ref(false) // Track if using pre-aggregated data

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: '24H Rate', value: 'rate24h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

const periods = [
  { label: 'Live', value: 8, isLive: true },
  { label: '1D', value: 24, isLive: false },
  { label: '3D', value: 72, isLive: false },
  { label: '7D', value: 168, isLive: false },
  { label: '30D', value: 720, isLive: false },
  { label: '3M', value: 2160, isLive: false },
  { label: '6M', value: 4320, isLive: false }
]

// Dynamic DEX config loaded from API
const availableDexes = ref([]) // List of DEX names
const dexConfigs = ref({}) // Map of dexName -> { name, color }

// DEXes that have data in the current historyData
const dexesWithData = computed(() => {
  const uniqueDexes = [...new Set(historyData.value.map(d => d.dex))]
  return uniqueDexes.filter(dex => availableDexes.value.includes(dex)).sort()
})

const dexes = computed(() => ['All', ...dexesWithData.value])

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

// Aggregate data by dynamic intervals based on total duration
const aggregateByInterval = (data, totalHours) => {
  const buckets = new Map()
  let intervalMinutes = 15 // Default 15 min for 24h

  if (totalHours > 24 && totalHours <= 168) {
    intervalMinutes = 120 // 2 hours for 7D
  } else if (totalHours > 168) {
    intervalMinutes = 1440 // 1 day for 30D
  }

  for (const item of data) {
    const date = new Date(item.timestamp)
    // Normalize to the start of the interval
    // For large intervals, we might want to just round to the nearest hour or day
    let normalizedDate;
    
    if (intervalMinutes === 1440) {
        // Round to start of day
        normalizedDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())
    } else {
        const totalMinutes = date.getHours() * 60 + date.getMinutes()
        const bucketIndex = Math.floor(totalMinutes / intervalMinutes)
        const normalizedTotalMinutes = bucketIndex * intervalMinutes
        const nsHours = Math.floor(normalizedTotalMinutes / 60)
        const nsMinutes = normalizedTotalMinutes % 60
        normalizedDate = new Date(date.getFullYear(), date.getMonth(), date.getDate(), nsHours, nsMinutes)
    }

    const intervalKey = normalizedDate.toISOString()
    const bucketKey = `${item.dex}-${intervalKey}`

    // Ensure we keep the latest entry for each interval
    if (!buckets.has(bucketKey) || new Date(buckets.get(bucketKey).timestamp) < date) {
      buckets.set(bucketKey, {
        ...item,
        timestamp: normalizedDate.toISOString()
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
    const hours = selectedPeriod.value

    // For <= 24H: use 5-minute aggregated data
    // For 1D-7D (>24h to <=168h): use hourly aggregated data
    // For 30D+ (>168h): use daily aggregated data
    if (hours <= 24) {
      // Use 5-minute aggregated API for Live/24H view
      try {
        const response = await chartApi.getComparisonData(props.symbol, hours)
        if (response.data && Object.keys(response.data).length > 0) {
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
      } catch (e) {
        console.log('[Chart] Pre-aggregated API not available, falling back to raw data')
      }
    } else if (hours <= 168) {
      // Use hourly aggregated API for 1D/3D/7D views
      try {
        const response = await chartApi.getHourlyComparisonData(props.symbol, hours)
        if (response.data && Object.keys(response.data).length > 0) {
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
      } catch (e) {
        console.log('[Chart] Hourly API not available:', e)
      }
    } else {
      // Use daily aggregated API for 30D+ views
      try {
        const days = Math.ceil(hours / 24)
        const response = await chartApi.getDailyComparisonData(props.symbol, days)
        if (response.data && Object.keys(response.data).length > 0) {
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
      } catch (e) {
        console.log('[Chart] Daily API not available:', e)
      }
    }

    // Fallback to raw data if pre-aggregated didn't return data
    // Note: This only works for 24H since raw data is cleaned up
    if (data.length === 0 && hours <= 24) {
      for (const dexName of availableDexes.value) {
        try {
          const response = await fundingRateApi.getHistory({
            dex: dexName,
            symbol: props.symbol,
            hours: hours
          })
          data.push(...response.data.map(d => ({ ...d, dex: dexName })))
        } catch (e) {
          console.error(`No ${dexName} data`, e)
        }
      }
    }

    // For 24H view, aggregate by 15min intervals for smoother charts
    // For daily data, it's already aggregated by day, so skip frontend aggregation
    let finalData = data
    if (hours <= 24 && data.length > 0) {
      finalData = aggregateByInterval(data, hours)
    }
    
    historyData.value = finalData.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))

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

// Watch for DEX changes - just re-render chart (data is already loaded)
watch(() => selectedDex.value, () => {
  // No need to refetch, just trigger chart re-render
  chartKey.value++
})

// Watch for Period changes
watch(() => selectedPeriod.value, async () => {
  isChangingPeriod.value = true
  await fetchData(false)
  isChangingPeriod.value = false
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
    // Only auto-refresh for Live mode (value = 8)
    const isLive = selectedPeriod.value === 8
    if (isLive) {
      fetchData(true)
    }
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

    // 2. Create labels
    const labels = allTimestamps.map(t => {
      const date = new Date(t)
      if (selectedPeriod.value > 24) {
        // Show Date + Month for longer periods
        // If it's a daily view (30D), just show Date
        if (selectedPeriod.value > 168) {
             return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
        }
        return date.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})
      }
      return date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})
    })

    // 3. Map data to these timestamps
    const getDataset = (dexName, color) => {
        const metricsData = allTimestamps.map(t => {
            const entry = historyData.value.find(d => d.dex === dexName && d.timestamp === t)
            if (!entry) return null
            let val
            if (selectedMetric.value === 'rate') {
              val = entry.rate * 100 // Convert rate to %
            } else if (selectedMetric.value === 'rate24h') {
              // 24h rate = 8h rate * 3 (3 funding periods per day)
              val = (entry.rate8h || 0) * 3
            } else {
              val = entry[selectedMetric.value]
            }
            return val || 0
        })

        return {
            label: dexName,
            borderColor: color,
            backgroundColor: 'transparent',
            data: metricsData,
            tension: 0.4,
            spanGaps: true,
            pointStyle: 'circle',
            pointRadius: window.innerWidth < 640 ? 1 : 2,
            pointBackgroundColor: color,
            pointHoverRadius: 6,
            pointHoverBackgroundColor: color,
            pointHoverBorderColor: '#000',
            pointHoverBorderWidth: 2,
            borderWidth: 2.5,
            fill: false
        }
    }

    const datasets = []
    // Dynamically build datasets for DEXes with data
    for (const dexName of dexesWithData.value) {
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
      usePointStyle: true,
      boxPadding: 4,
      titleFont: {
        size: window.innerWidth < 640 ? 11 : 12
      },
      bodyFont: {
        size: window.innerWidth < 640 ? 10 : 12
      },
      animation: false,
      callbacks: {
        label: function(context) {
          let label = context.dataset.label || ''
          if (label) {
            label += ': '
          }
          if (context.parsed.y !== null) {
            const decimals = selectedMetric.value === 'rate' ? 6 :
                            selectedMetric.value === 'rate8h' ? 4 :
                            selectedMetric.value === 'rate24h' ? 3 : 2
            label += context.parsed.y.toFixed(decimals) + '%'
          }
          return label
        },
        title: function(context) {
            // Customize title based on period
            if (context[0] && selectedPeriod.value > 24) {
                 return context[0].label 
            }
            return context[0] ? context[0].label : ''
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
        maxTicksLimit: window.innerWidth < 640 ? 6 : 10,
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
  <div class="flex flex-col relative w-full h-full min-h-[450px] p-4 sm:p-6">
    <!-- Chart Header -->
    <div class="flex flex-col gap-3 sm:gap-4 mb-4">
      <div class="flex items-center justify-between">
        <h2 class="text-base sm:text-lg font-medium text-neutral-200 tracking-tight">Funding Rate History</h2>

        <!-- Custom Legend (desktop) -->
        <div class="hidden sm:flex items-center gap-3 lg:gap-4 text-xs">
          <div 
            v-for="dexName in dexesWithData" 
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
      <div class="flex flex-col sm:flex-row gap-2 sm:gap-0 sm:items-center sm:bg-white/5 sm:rounded-lg sm:p-1 sm:w-fit">
        <!-- DEX Tabs -->
        <div class="flex items-center bg-white/5 sm:bg-transparent rounded-lg p-1 sm:p-0 overflow-x-auto">
          <button
            v-for="dex in dexes"
            :key="dex"
            @click="selectedDex = dex"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors whitespace-nowrap flex-shrink-0',
              selectedDex === dex
                ? 'bg-neutral-800 text-white shadow-sm border border-white/10'
                : 'text-neutral-500 hover:text-white'
            ]"
          >
            {{ dex }}
          </button>
        </div>

        <!-- Separator (desktop only) -->
        <div class="hidden sm:block w-px h-4 bg-white/10 mx-2"></div>
        
        <!-- Period Tabs (NEW) -->
        <div class="flex items-center bg-white/5 sm:bg-transparent rounded-lg p-1 sm:p-0 overflow-x-auto">
          <button
            v-for="period in periods"
            :key="period.value"
            @click="selectedPeriod = period.value"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors whitespace-nowrap flex-shrink-0',
              selectedPeriod === period.value
                ? 'bg-neutral-800 text-white shadow-sm border border-white/10'
                : 'text-neutral-500 hover:text-white'
            ]"
          >
            {{ period.label }}
          </button>
        </div>

        <!-- Separator (desktop only) -->
        <div class="hidden sm:block w-px h-4 bg-white/10 mx-2"></div>

        <!-- Metric Tabs -->
        <div class="flex items-center bg-white/5 sm:bg-transparent rounded-lg p-1 sm:p-0 overflow-x-auto">
          <button
            v-for="metric in metrics"
            :key="metric.value"
            @click="selectedMetric = metric.value"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors whitespace-nowrap flex-shrink-0',
              selectedMetric === metric.value
                ? 'bg-neutral-800 text-white shadow-sm border border-white/10'
                : 'text-neutral-500 hover:text-white'
            ]"
          >
            {{ metric.label }}
          </button>
        </div>
      </div>

      <!-- Custom Legend (mobile only) -->
      <div class="flex sm:hidden items-center gap-3 text-[10px]">
        <div 
          v-for="dexName in dexesWithData" 
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
    <div v-else class="flex-1 relative w-full min-h-[300px]">
      <!-- Overlay loading for changes -->
      <div
        v-if="isChangingMetric || isChangingDex || isChangingSymbol || isChangingPeriod"
        class="absolute inset-0 bg-[#0A0A0A]/60 backdrop-blur-[1px] flex items-center justify-center z-20 rounded-lg transition-opacity duration-150"
      >
        <div class="flex items-center gap-2 sm:gap-3">
          <div class="w-4 h-4 sm:w-5 sm:h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-xs sm:text-sm text-neutral-300">
            {{ isChangingSymbol ? 'Loading asset...' : isChangingPeriod ? 'Loading history...' : isChangingDex ? 'Loading platform...' : 'Updating...' }}
          </span>
        </div>
      </div>
      <Line :key="chartKey" :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
