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
const isChangingMetric = ref(false) // Loading state for metric changes

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
// Watch for symbol/dex changes - show loading
watch(() => [props.symbol, selectedDex.value], () => fetchData(false), { immediate: true })

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
          <div v-if="selectedDex === 'All' || selectedDex === 'Paradex'" class="flex items-center gap-1.5 sm:gap-2">
            <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]"></span>
            <span class="text-neutral-400">Paradex</span>
          </div>
          <div v-if="selectedDex === 'All' || selectedDex === 'Lighter'" class="flex items-center gap-1.5 sm:gap-2">
            <span class="w-2 h-2 rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.6)]"></span>
            <span class="text-neutral-400">Lighter</span>
          </div>
          <div v-if="selectedDex === 'All' || selectedDex === 'Hyperliquid'" class="flex items-center gap-1.5 sm:gap-2">
            <span class="w-2 h-2 rounded-full bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.6)]"></span>
            <span class="text-neutral-400">Hyperliquid</span>
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
        <div v-if="selectedDex === 'All' || selectedDex === 'Paradex'" class="flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.6)]"></span>
          <span class="text-neutral-400">Paradex</span>
        </div>
        <div v-if="selectedDex === 'All' || selectedDex === 'Lighter'" class="flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-indigo-500 shadow-[0_0_6px_rgba(99,102,241,0.6)]"></span>
          <span class="text-neutral-400">Lighter</span>
        </div>
        <div v-if="selectedDex === 'All' || selectedDex === 'Hyperliquid'" class="flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-amber-500 shadow-[0_0_6px_rgba(245,158,11,0.6)]"></span>
          <span class="text-neutral-400">Hyperliquid</span>
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
      <!-- Overlay loading for metric changes -->
      <div
        v-if="isChangingMetric"
        class="absolute inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-20 rounded-lg transition-opacity duration-150"
      >
        <div class="flex items-center gap-2 sm:gap-3">
          <div class="w-4 h-4 sm:w-5 sm:h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-xs sm:text-sm text-neutral-300">Updating...</span>
        </div>
      </div>
      <Line :key="chartKey" :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
