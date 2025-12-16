<script setup>
// Nuxt auto-imports: ref, computed, watch, onMounted, onUnmounted
import { fundingRateApi, chartApi, dexApi, arbitrageApi } from '~/services/api'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import { Bar } from 'vue-chartjs'

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend
)

const props = defineProps({
  symbol: {
    type: String,
    required: true
  },
  longDex: {
    type: String,
    default: null
  },
  shortDex: {
    type: String,
    default: null
  }
})

const loading = ref(true)
const isInitialLoad = ref(true)
const chartKey = ref(0) // Used to force chart re-render
const historyData = ref([])
const selectedMetric = ref('rateAnnual') // 'rate', 'rate8h', 'rateAnnual'
const selectedPeriod = ref(8) // Default to Live (8h)
const isChangingMetric = ref(false) // Loading state for metric changes
const isChangingSymbol = ref(false) // Loading state for symbol changes
const isChangingPeriod = ref(false)
const isChangingDex = ref(false) // Loading state for DEX changes (for animation)
const usingPreAggregated = ref(false) // Track if using pre-aggregated data

// Data from funding_rate_daily table for accurate yield calculation (historical)
const dailyRatesData = ref(null) // { symbols: [...], periodLabel, dataPointCount }

// Data from last_funding table for accurate yield calculation (live)
const liveRatesData = ref(null) // [{ symbol, rates: [...] }]

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

const periods = [
  { label: 'Live', value: 8, days: 0, isLive: true },  // Real-time data (last 8h)
  { label: '1D', value: 24, days: 1, isLive: false },   // Previous 24 hours
  { label: '3D', value: 72, days: 3, isLive: false },
  { label: '7D', value: 168, days: 7, isLive: false },
  { label: '30D', value: 720, days: 30, isLive: false },
  { label: '3M', value: 2160, days: 90, isLive: false },
  { label: '6M', value: 4320, days: 180, isLive: false }
]

const emit = defineEmits(['update:averageYield'])

// Dynamic DEX config loaded from API
const availableDexes = ref([]) // List of DEX names
const dexConfigs = ref({}) // Map of dexName -> { name, color }

// DEXes that have data in the current historyData
const dexesWithData = computed(() => {
  const uniqueDexes = [...new Set(historyData.value.map(d => d.dex))]
  return uniqueDexes.filter(dex => availableDexes.value.includes(dex)).sort()
})

const dexes = computed(() => ['All', ...dexesWithData.value])

// Computed: Human-readable timeframe description based on selected period
const timeframeDescription = computed(() => {
  const now = new Date()
  const currentPeriod = periods.find(p => p.value === selectedPeriod.value)
  
  if (!currentPeriod) return ''
  
  const formatDate = (date, includeTime = false) => {
    const options = { month: 'short', day: 'numeric' }
    if (includeTime) {
      return date.toLocaleDateString('en-US', options) + ' ' + date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
    }
    return date.toLocaleDateString('en-US', options)
  }
  
  if (currentPeriod.isLive) {
    // Live: use actual first data point from chart
    if (historyData.value && historyData.value.length > 0) {
      const timestamps = historyData.value.map(d => new Date(d.timestamp)).sort((a, b) => a - b)
      const firstTime = timestamps[0]
      const lastTime = timestamps[timestamps.length - 1]
      return `${formatDate(firstTime, true)} - ${formatDate(lastTime, true)}`
    }
    // Fallback if no data
    const startTime = new Date(now.getTime() - 8 * 60 * 60 * 1000)
    return `${formatDate(startTime, true)} - Now`
  } else {
    // Historical: show date range
    const endDate = new Date(now)
    endDate.setDate(endDate.getDate() - 1) // Yesterday as end (daily data is from previous day)
    const startDate = new Date(endDate)
    startDate.setDate(startDate.getDate() - currentPeriod.days + 1)
    
    if (currentPeriod.days === 1) {
      return `Yesterday (${formatDate(endDate)})`
    } else if (startDate.getMonth() === endDate.getMonth()) {
      return `${startDate.getDate()} - ${formatDate(endDate)}`
    } else {
      return `${formatDate(startDate)} - ${formatDate(endDate)}`
    }
  }
})

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

    // For <= 24H: use 5-minute aggregated data (Live)
    // For 1D-7D (>24h to <=168h): use hourly aggregated data
    // For 30D+ (>168h): use daily aggregated data
    if (hours <= 24) {
      // Use 5-minute aggregated API for Live view
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

    let finalData = data
    // Only aggregate if data was NOT already pre-aggregated from backend
    // Pre-aggregated data already has correct resolution (5min for live, hourly for 1D-7D, daily for 30D+)
    if (hours <= 24 && data.length > 0 && !usedPreAggregated) {
      finalData = aggregateByInterval(data, hours)
    }
    
    historyData.value = finalData.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))

    usingPreAggregated.value = usedPreAggregated
    chartKey.value++
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
    isInitialLoad.value = false
  }
}

// Fetch daily aggregated data from funding_rate_daily table for accurate yield calculation
// This is separate from chart data and used only for the Strategy Card yield display
const fetchDailyYield = async () => {
  try {
    const currentPeriod = periods.find(p => p.value === selectedPeriod.value)
    if (!currentPeriod || currentPeriod.isLive) {
      dailyRatesData.value = null
      return
    }
    
    const days = currentPeriod.days
    const response = await arbitrageApi.getHistoricalRates(days)
    dailyRatesData.value = response.data
  } catch (error) {
    console.error('Failed to fetch daily rates:', error)
    dailyRatesData.value = null
  }
}

// Fetch live rates from last_funding table for accurate Live mode yield calculation
const fetchLiveRates = async () => {
  try {
    const response = await arbitrageApi.getAllRates()
    liveRatesData.value = response.data
  } catch (error) {
    console.error('Failed to fetch live rates:', error)
    liveRatesData.value = null
  }
}

watch(() => props.symbol, async () => {
  isChangingSymbol.value = true
  await Promise.all([fetchData(false), fetchDailyYield(), fetchLiveRates()])
  isChangingSymbol.value = false
})

// Watch for DEX selection changes from parent
watch([() => props.longDex, () => props.shortDex], () => {
  isChangingDex.value = true
  chartKey.value++
  // Reset after animation completes
  setTimeout(() => {
    isChangingDex.value = false
  }, 800)
})

watch(() => selectedPeriod.value, async () => {
  isChangingPeriod.value = true
  await Promise.all([fetchData(false), fetchDailyYield()])
  isChangingPeriod.value = false
})

onMounted(async () => {
  await loadDexConfigs()
  await Promise.all([fetchData(false), fetchDailyYield(), fetchLiveRates()])
})

watch(() => selectedMetric.value, async () => {
  isChangingMetric.value = true
  await new Promise(resolve => setTimeout(resolve, 150))
  chartKey.value++
  isChangingMetric.value = false
})

onMounted(() => {
  const refreshInterval = setInterval(() => {
    if (selectedPeriod.value === 24) {
      fetchData(true)
    }
  }, 10000)
  
  onUnmounted(() => {
    clearInterval(refreshInterval)
  })
})

const unifiedChartData = computed(() => {
    // Explicit dependency on DEX props for reactivity
    const longDex = props.longDex
    const shortDex = props.shortDex
    
    // 1. Collect all unique timestamps, sorted
    const allTimestamps = [...new Set(historyData.value.map(d => d.timestamp))].sort()

    // 2. Create labels
    const labels = allTimestamps.map(t => {
      const date = new Date(t)
      if (selectedPeriod.value > 24) {
        if (selectedPeriod.value > 168) {
             return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
        }
        return date.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})
      }
      return date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})
    })

    // 3. Calculate Spread between longDex and shortDex for each timestamp (for chart display)
    const spreadData = allTimestamps.map(t => {
        const entries = historyData.value.filter(d => d.timestamp === t)
        
        const metric = selectedMetric.value
        
        const getVal = (entry) => {
             let v = entry[metric]
             if (metric === 'rate') v *= 100
             return v
        }

        // Find entries for the two selected DEXes
        const longEntry = entries.find(d => d.dex === longDex)
        const shortEntry = entries.find(d => d.dex === shortDex)
        
        if (!longEntry || !shortEntry) return 0
        
        const longVal = getVal(longEntry)
        const shortVal = getVal(shortEntry)
        
        // Directional spread: what you receive (short) minus what you pay (long)
        return shortVal - longVal
    })
    
    // 4. Calculate the ANNUALIZED average for the Strategy card
    // Use data from funding_rate_daily table when available (more accurate)
    // Fall back to chart data for Live view
    const currentPeriod = periods.find(p => p.value === selectedPeriod.value)
    const isLive = currentPeriod?.isLive || false
    
    let yieldValue = 0
    let dataPointCount = 0
    
    if (!isLive && dailyRatesData.value && dailyRatesData.value.symbols) {
      // Use data from funding_rate_daily table for historical periods
      const symbolData = dailyRatesData.value.symbols.find(s => s.symbol === props.symbol)
      if (symbolData && symbolData.rates) {
        const longRate = symbolData.rates.find(r => r.dex === longDex)
        const shortRate = symbolData.rates.find(r => r.dex === shortDex)
        
        if (longRate && shortRate) {
          // Calculate spread: shortRate - longRate (annualized)
          const longVal = parseFloat(longRate.rateAnnual) || 0
          const shortVal = parseFloat(shortRate.rateAnnual) || 0
          yieldValue = shortVal - longVal
        }
      }
      dataPointCount = dailyRatesData.value.dataPointCount || 0
    } else {
      // For Live view, use data from last_funding table (via arbitrage/rates API)
      if (liveRatesData.value) {
        const symbolData = liveRatesData.value.find(s => s.symbol === props.symbol)
        if (symbolData && symbolData.rates) {
          const longRate = symbolData.rates.find(r => r.dex === longDex)
          const shortRate = symbolData.rates.find(r => r.dex === shortDex)
          
          if (longRate && shortRate) {
            const longVal = parseFloat(longRate.rateAnnual) || 0
            const shortVal = parseFloat(shortRate.rateAnnual) || 0
            yieldValue = shortVal - longVal
          }
        }
      }
      dataPointCount = 1 // Live uses current snapshot
    }
    
    // Emit annualized yield to parent (this is always the annualized rate)
    emit('update:averageYield', {
      average: yieldValue,  // This is the annualized yield from daily table
      periodLabel: currentPeriod?.label || 'Live',
      periodDays: isLive ? 1 : (currentPeriod?.days || 1),
      dataPoints: dataPointCount,
      isLive: isLive
    })

    return {
        labels,
        datasets: [{
            label: 'Potential Yield',
            data: spreadData,
            backgroundColor: spreadData.map(val => {
              if (val >= 0) {
                return 'rgba(16, 185, 129, 0.6)' // Emerald/Green for positive
              } else {
                return 'rgba(239, 68, 68, 0.6)' // Red for negative
              }
            }),
            borderColor: spreadData.map(val => {
              if (val >= 0) {
                return '#10B981' // Emerald border
              } else {
                return '#EF4444' // Red border
              }
            }),
            borderWidth: 1,
            borderRadius: 6,
            borderSkipped: false,
            hoverBackgroundColor: spreadData.map(val => val >= 0 ? '#34D399' : '#F87171'),
            hoverBorderColor: spreadData.map(val => val >= 0 ? '#6EE7B7' : '#FCA5A5'),
            hoverBorderWidth: 2,
            barPercentage: 0.6,
            categoryPercentage: 0.8
        }]
    }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: {
    duration: (isInitialLoad.value || isChangingDex.value) ? 750 : 0
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
      padding: 12,
      displayColors: true,
      callbacks: {
        label: function(context) {
          let label = context.dataset.label || ''
          if (label) {
            label += ': '
          }
          if (context.parsed.y !== null) {
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
      title: {
        display: true,
        text: 'Yield (%)',
        color: '#6B7280',
        font: { size: 10, weight: 'normal' }
      },
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
      },
      beginAtZero: true
    },
    x: {
      grid: {
        display: false
      },
      ticks: {
        color: '#6B7280',
        font: { size: 10 },
        maxTicksLimit: 10,
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
  <div class="flex flex-col relative w-full h-full min-h-[450px] p-4 sm:p-6">
    <!-- Chart Header -->
    <div class="flex flex-col gap-3 sm:gap-4 mb-4">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-base sm:text-lg font-medium text-neutral-200 tracking-tight">Arbitrage Yield History</h2>
          <p class="text-[10px] sm:text-xs text-neutral-500 mt-0.5">{{ timeframeDescription }}</p>
        </div>
      </div>

      <!-- Controls -->
      <div class="flex flex-col sm:flex-row gap-2 sm:gap-0 sm:items-center sm:bg-white/5 sm:rounded-lg sm:p-1 sm:w-fit">
        <!-- Period Tabs -->
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
    </div>

    <!-- Loading State -->
    <div v-if="loading && isInitialLoad" class="flex-1 flex items-center justify-center">
      <div class="flex flex-col items-center gap-3">
        <div class="w-6 h-6 sm:w-8 sm:h-8 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
        <span class="text-xs sm:text-sm text-neutral-500">Loading chart data...</span>
      </div>
    </div>

    <!-- Chart -->
    <div v-else class="flex-1 relative w-full min-h-[300px]">
      <div
        v-if="isChangingMetric || isChangingSymbol || isChangingPeriod"
        class="absolute inset-0 bg-[#0A0A0A]/60 backdrop-blur-[1px] flex items-center justify-center z-20 rounded-lg transition-opacity duration-150"
      >
        <div class="flex items-center gap-2 sm:gap-3">
          <div class="w-4 h-4 sm:w-5 sm:h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-xs sm:text-sm text-neutral-300">
            {{ isChangingSymbol ? 'Loading asset...' : isChangingPeriod ? 'Loading history...' : 'Updating...' }}
          </span>
        </div>
      </div>
      <!-- Use Bar chart component here -->
      <Bar :key="chartKey" :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
