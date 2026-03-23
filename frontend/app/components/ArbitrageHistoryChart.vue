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

// Helper to parse timestamp as UTC (backend returns timestamps without Z suffix)
const parseAsUTC = (timestamp) => {
  const ts = String(timestamp)
  // If already has timezone info, parse as-is
  if (ts.endsWith('Z') || ts.includes('+') || ts.includes('-', 10)) {
    return new Date(ts)
  }
  // Otherwise, append Z to force UTC interpretation
  return new Date(ts + 'Z')
}

// Data from funding_rate_daily table for accurate yield calculation (historical)
const dailyRatesData = ref(null) // { symbols: [...], periodLabel, dataPointCount }

// Data from last_funding table for accurate yield calculation (live)
const liveRatesData = ref(null) // [{ symbol, rates: [...] }]

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: '24H Rate', value: 'rate24h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

const periods = [
  { label: 'Live', value: 8, days: 0, isLive: true },  // Real-time data (last 8h)
  { label: '1D', value: 24, days: 1, isLive: false },   // Previous 24 hours
  { label: '3D', value: 72, days: 3, isLive: false },
  { label: '7D', value: 168, days: 7, isLive: false },
  { label: '30D', value: 720, days: 30, isLive: false },
  { label: '3M', value: 2160, days: 90, isLive: false },
  { label: '6M', value: 4320, days: 180, isLive: false },
  { label: 'Custom', value: -1, days: 0, isLive: false, isCustom: true }  // Custom timeframe
]

// Custom timeframe state
const customDateRange = ref(null) // [startDate, endDate]
const customTimeframeActive = ref(false)
const customTimeframeLabel = ref('')

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

// Handle custom period selection  
const selectPeriod = (period) => {
  if (!period.isCustom) {
    customTimeframeActive.value = false
    customDateRange.value = null
    selectedPeriod.value = period.value
  }
}

// Compute the data precision based on selected date range
const customPrecision = computed(() => {
  if (!customDateRange.value || !customDateRange.value[0]) {
    return { label: 'Select dates', interval: null, enableTime: false }
  }
  
  const now = new Date()
  const start = new Date(customDateRange.value[0])
  
  // How many days ago is the start date?
  const daysAgoStart = (now - start) / (1000 * 60 * 60 * 24)
  
  if (daysAgoStart <= 1) {
    return { 
      label: '5-min precision', 
      interval: '5min',
      description: 'Data every 5 minutes',
      enableTime: true
    }
  } else if (daysAgoStart <= 7) {
    return { 
      label: 'Hourly precision', 
      interval: 'hourly',
      description: 'Data rounded to hour',
      enableTime: true
    }
  } else {
    return { 
      label: 'Daily precision', 
      interval: 'daily',
      description: 'Daily data only',
      enableTime: false
    }
  }
})

// Format date for display
const formatDateLabel = (date, precision) => {
  if (!date) return ''
  const d = new Date(date)
  
  if (precision === 'daily') {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
  } else {
    const dateStr = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
    const hours = d.getUTCHours().toString().padStart(2, '0')
    const mins = d.getUTCMinutes().toString().padStart(2, '0')
    return `${dateStr} ${hours}:${mins}`
  }
}

// Handle date range change from picker
const onDateRangeChange = (range) => {
  if (!range || !range[0] || !range[1]) return
  
  const start = new Date(range[0])
  const end = new Date(range[1])
  const diffHours = Math.ceil((end - start) / (1000 * 60 * 60))
  
  if (diffHours <= 0) return
  
  // Format label
  const precision = customPrecision.value.interval || 'daily'
  const startLabel = formatDateLabel(start, precision)
  const endLabel = formatDateLabel(end, precision)
  
  selectedPeriod.value = diffHours
  customTimeframeActive.value = true
  customTimeframeLabel.value = `${startLabel} → ${endLabel}`
}

// Computed: Human-readable timeframe description based on selected period
const timeframeDescription = computed(() => {
  const currentPeriod = periods.find(p => p.value === selectedPeriod.value)
  
  if (!currentPeriod) return ''
  
  // Format date in UTC to avoid timezone confusion
  const formatDateUTC = (date, includeTime = false) => {
    const options = { month: 'short', day: 'numeric', timeZone: 'UTC' }
    const dateStr = date.toLocaleDateString('en-US', options)
    if (includeTime) {
      const timeStr = date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC' })
      return `${dateStr} ${timeStr}`
    }
    return dateStr
  }
  
  // Get yesterday's date in UTC
  const getYesterdayUTC = () => {
    const now = new Date()
    const yesterday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1))
    return yesterday
  }
  
  if (currentPeriod.isLive) {
    // Live: use actual first data point from chart (today's data)
    if (historyData.value && historyData.value.length > 0) {
      const timestamps = historyData.value.map(d => parseAsUTC(d.timestamp)).sort((a, b) => a - b)
      const firstTime = timestamps[0]
      const lastTime = timestamps[timestamps.length - 1]
      return `Today (UTC): ${formatDateUTC(firstTime, true)} - ${formatDateUTC(lastTime, true)}`
    }
    return `Today (UTC) - loading...`
  } else if (currentPeriod.days === 1) {
    // 1D: yesterday's data in UTC
    const yesterday = getYesterdayUTC()
    if (historyData.value && historyData.value.length > 0) {
      const timestamps = historyData.value.map(d => parseAsUTC(d.timestamp)).sort((a, b) => a - b)
      const firstTime = timestamps[0]
      const lastTime = timestamps[timestamps.length - 1]
      return `${formatDateUTC(yesterday)} (UTC): ${formatDateUTC(firstTime, true)} - ${formatDateUTC(lastTime, true)}`
    }
    return `${formatDateUTC(yesterday)} (UTC)`
  } else {
    // Historical: show date range ending at yesterday UTC
    const yesterday = getYesterdayUTC()
    const startDate = new Date(yesterday)
    startDate.setUTCDate(startDate.getUTCDate() - currentPeriod.days + 1)
    
    return `${formatDateUTC(startDate)} - ${formatDateUTC(yesterday)} (UTC)`
  }
})

// Computed: Description of what each bar represents (aggregation interval)
const barIntervalDescription = computed(() => {
  const hours = selectedPeriod.value
  const currentPeriod = periods.find(p => p.value === hours)
  
  if (currentPeriod?.isLive) {
    return '5-min averages'
  } else if (hours === 24) {
    // 1D uses 15-min aggregated data
    return '15-min averages'
  } else if (hours <= 168) {
    // 3D, 7D use hourly averages
    return 'Hourly averages'
  } else {
    // 30D, 3M, 6M use daily averages
    return 'Daily averages'
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
    const periodConfig = periods.find(p => p.value === selectedPeriod.value)
    let hours = selectedPeriod.value
    
    // Check if this is a custom timeframe
    const now = new Date()
    const todayMidnightUTC = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0))
    const isCustom = customTimeframeActive.value && customDateRange.value
    
    // ============================================
    // CUSTOM RANGE: Use SINGLE data source based on range duration
    // Same logic as preset periods to avoid mixing granularities
    // ============================================
    if (isCustom && customDateRange.value) {
      const customStart = new Date(customDateRange.value[0])
      const customEnd = new Date(customDateRange.value[1])
      const totalHours = Math.ceil((customEnd - customStart) / (1000 * 60 * 60))
      const totalDays = Math.ceil(totalHours / 24)
      
      // Select a SINGLE data source based on range duration
      // This mirrors the preset period logic and prevents mixed granularities
      const pushResponseData = (response) => {
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
        }
      }
      
      try {
        if (totalDays <= 1) {
          // ≤ 1 day: use 5-min API (same as Live/1D presets)
          const hoursFromNow = Math.ceil((now - customStart) / (1000 * 60 * 60)) + 1
          const response = await chartApi.getComparisonData(props.symbol, hoursFromNow)
          pushResponseData(response)
        } else if (totalDays <= 7) {
          // 1-7 days: use hourly API only (same as 3D/7D presets)
          const hoursFromNow = Math.ceil((now - customStart) / (1000 * 60 * 60)) + 1
          const response = await chartApi.getHourlyComparisonData(props.symbol, hoursFromNow)
          pushResponseData(response)
        } else {
          // > 7 days: use daily API ONLY (same as 30D/3M/6M presets)
          const response = await chartApi.getDailyComparisonData(props.symbol, totalDays)
          pushResponseData(response)
        }
      } catch (e) {
        console.log('[Custom] API not available for range:', totalDays, 'days', e)
      }
      
      // Filter to only show data within the custom range
      // Add 59 seconds to end time so exact minute matches are included
      // (e.g., selecting 23:50 includes 23:50:00, but 23:48 excludes 23:50:00)
      // Also cap at current time so future times don't affect calculations
      let effectiveEnd = customEnd.getTime() + 59 * 1000
      const nowTime = now.getTime()
      if (effectiveEnd > nowTime) {
        effectiveEnd = nowTime // Cap at current time
      }
      const customEndWithBuffer = new Date(effectiveEnd)
      
      let finalData = data.filter(d => {
        const timestamp = parseAsUTC(d.timestamp)
        return timestamp >= customStart && timestamp <= customEndWithBuffer
      })
      
      historyData.value = finalData.sort((a, b) => parseAsUTC(a.timestamp) - parseAsUTC(b.timestamp))
      usingPreAggregated.value = true
      chartKey.value++
      return // Exit early for custom ranges
    }
    
    // ============================================
    // PRESET PERIODS: Original logic
    // ============================================
    
    // Dynamic hours calculation for Live and 1D
    if (periodConfig?.isLive) {
      // Live: data from today UTC (since midnight UTC), no fallback
      const midnightUTC = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0))
      const hoursSinceMidnightUTC = (now - midnightUTC) / (1000 * 60 * 60)
      hours = Math.max(hoursSinceMidnightUTC, 0.1) // Small minimum to avoid 0
    } else if (selectedPeriod.value === 24) {
      // 1D: yesterday's complete data (24h offset by 24h)
      // We'll fetch 48h and filter to yesterday
      hours = 48
    }

    // For <= 24H: use 5-minute aggregated data (Live)
    // For 1D-7D (>24h to <=168h): use hourly aggregated data
    // For 30D+ (>168h): use daily aggregated data
    if (periodConfig?.isLive || selectedPeriod.value === 24) {
      // Use 5-minute aggregated API for Live view and 1D
      try {
        const response = await chartApi.getComparisonData(props.symbol, Math.ceil(hours))
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
      
      // For 1D: filter to only yesterday's data (UTC)
      if (selectedPeriod.value === 24 && data.length > 0) {
        // Yesterday midnight UTC
        const yesterdayMidnightUTC = new Date(todayMidnightUTC.getTime() - 24 * 60 * 60 * 1000)
        
        // Keep only data from yesterday UTC (between yesterdayMidnightUTC and todayMidnightUTC)
        const filteredData = data.filter(d => {
          const timestamp = new Date(d.timestamp)
          return timestamp >= yesterdayMidnightUTC && timestamp < todayMidnightUTC
        })
        
        // Replace data with filtered data
        data.length = 0
        data.push(...filteredData)
      }
    } else if (hours <= 168) {
      // Use hourly aggregated API for 3D/7D views
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
    if (hours <= 24 && data.length > 0 && !usedPreAggregated) {
      finalData = aggregateByInterval(data, hours)
    }
    
    // For 1D view ONLY: aggregate 5-min data to 15-min intervals (reduce 288 bars to 96)
    if (selectedPeriod.value === 24 && finalData.length > 0) {
      finalData = aggregateByInterval(finalData, 24)
    }
    
    // For LIVE mode: filter to only show TODAY's data (after midnight UTC)
    if (periodConfig?.isLive && finalData.length > 0) {
      finalData = finalData.filter(d => {
        const timestamp = parseAsUTC(d.timestamp)
        return timestamp >= todayMidnightUTC
      })
    }
    
    historyData.value = finalData.sort((a, b) => parseAsUTC(a.timestamp) - parseAsUTC(b.timestamp))

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

// Watch for custom date range changes (including time changes)
watch(() => customDateRange.value, async (newVal, oldVal) => {
  // Only refetch if custom timeframe is active and the value actually changed
  if (customTimeframeActive.value && newVal && newVal[0] && newVal[1]) {
    // Check if this is a real change (not just initialization)
    if (oldVal && oldVal[0] && oldVal[1]) {
      const oldStart = new Date(oldVal[0]).getTime()
      const oldEnd = new Date(oldVal[1]).getTime()
      const newStart = new Date(newVal[0]).getTime()
      const newEnd = new Date(newVal[1]).getTime()
      
      // Only refetch if the timestamps actually changed
      if (oldStart !== newStart || oldEnd !== newEnd) {
        isChangingPeriod.value = true
        await fetchData(false)
        isChangingPeriod.value = false
      }
    }
  }
}, { deep: true })

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
    // Auto-refresh only for Live mode (value = 8)
    const isLive = selectedPeriod.value === 8
    if (isLive) {
      fetchData(true)      // Refresh chart data
      fetchLiveRates()     // Refresh Strategy Card yield data
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

    // 2. Create labels (always in UTC, using parseAsUTC for consistent parsing)
    const labels = allTimestamps.map(t => {
      const date = parseAsUTC(t)
      if (selectedPeriod.value > 24) {
        if (selectedPeriod.value > 168) {
             return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
        }
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' }) + ' ' + date.toLocaleTimeString('en-US', {hour: '2-digit', minute:'2-digit', hour12: false, timeZone: 'UTC'})
      }
      return date.toLocaleTimeString('en-US', {hour: '2-digit', minute:'2-digit', hour12: false, timeZone: 'UTC'})
    })

    // 3. Calculate Spread between longDex and shortDex for each timestamp (for chart display)
    const spreadData = allTimestamps.map(t => {
        const entries = historyData.value.filter(d => d.timestamp === t)
        
        const metric = selectedMetric.value
        
        const getVal = (entry) => {
             let v
             if (metric === 'rate') {
               v = entry.rate * 100
             } else if (metric === 'rate24h') {
               // 24h rate = 8h rate * 3 (3 funding periods per day)
               v = (entry.rate8h || 0) * 3
             } else {
               v = entry[metric]
             }
             return v || 0
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
    
    // Calculate average from chart data (average of all spreads shown in chart)
    // This computes the spread at each timestamp using rateAnnual metric and averages them
    let chartAverageYield = 0
    let chartDataPointCount = 0
    let actualDays = 0
    let totalReturn = 0
    
    if (allTimestamps.length > 0 && longDex && shortDex) {
      const spreadsForAverage = []
      
      for (const t of allTimestamps) {
        const entries = historyData.value.filter(d => d.timestamp === t)
        const longEntry = entries.find(d => d.dex === longDex)
        const shortEntry = entries.find(d => d.dex === shortDex)
        
        if (longEntry && shortEntry) {
          // Use rateAnnual for consistency with yield display
          const longVal = longEntry.rateAnnual || 0
          const shortVal = shortEntry.rateAnnual || 0
          spreadsForAverage.push(shortVal - longVal)
        }
      }
      
      if (spreadsForAverage.length > 0) {
        chartAverageYield = spreadsForAverage.reduce((sum, v) => sum + v, 0) / spreadsForAverage.length
        chartDataPointCount = spreadsForAverage.length
      }
      
      // Calculate actual number of unique days covered by the data (using UTC)
      const uniqueDays = new Set()
      for (const t of allTimestamps) {
        const date = parseAsUTC(t)
        const dayKey = `${date.getUTCFullYear()}-${date.getUTCMonth()}-${date.getUTCDate()}`
        uniqueDays.add(dayKey)
      }
      actualDays = uniqueDays.size
      
      // Calculate total cumulative return for the period
      // NEW: Use actual time span covered by data, not just day count
      // This ensures adding a positive bar always increases total return
      if (spreadsForAverage.length > 0) {
        // Determine interval between data points (in hours)
        // Sort timestamps and calculate average interval
        const sortedTimestamps = allTimestamps.map(t => parseAsUTC(t).getTime()).sort((a, b) => a - b)
        let intervalHours = 24 // Default to daily
        
        if (sortedTimestamps.length >= 2) {
          // Calculate median interval between consecutive points (more robust than average)
          const intervals = []
          for (let i = 1; i < sortedTimestamps.length; i++) {
            intervals.push((sortedTimestamps[i] - sortedTimestamps[i-1]) / (1000 * 60 * 60))
          }
          intervals.sort((a, b) => a - b)
          intervalHours = intervals[Math.floor(intervals.length / 2)] // Median
          
          // Clamp to sensible values (5min = 0.083h, max = 24h)
          intervalHours = Math.max(0.083, Math.min(intervalHours, 24))
        }
        
        // Total Return = sum of (spread × interval_hours / (24 × 365)) for each point
        // This is equivalent to: sum of (annualized_spread × hours_covered / 8760)
        const hoursInYear = 24 * 365 // 8760
        totalReturn = spreadsForAverage.reduce((sum, spread) => {
          return sum + (spread * intervalHours / hoursInYear)
        }, 0)
      }
    }
    
    // Emit annualized yield to parent (this is always the annualized rate)
    emit('update:averageYield', {
      average: yieldValue,  // This is the annualized yield from daily table (snapshot for live, average for historical)
      averageFromChart: chartAverageYield,  // Average of all chart data points (spreads)
      chartDataPoints: chartDataPointCount,  // Number of data points in the chart average
      actualDays: actualDays,  // Number of unique days covered by the data
      totalReturn: totalReturn,  // Cumulative return for the period: (annual / 365) × days
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
        title: function(tooltipItems) {
          // For 1D view, prepend the actual UTC date instead of "Yesterday"
          const label = tooltipItems[0]?.label || ''
          if (selectedPeriod.value === 24) {
            const now = new Date()
            const yesterday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1))
            const dateStr = yesterday.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
            return `${dateStr} ${label}`
          }
          return label
        },
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
          <div class="flex items-center gap-2 mt-0.5">
            <p class="text-[10px] sm:text-xs text-neutral-500">{{ timeframeDescription }}</p>
            <span class="text-[9px] sm:text-[10px] text-neutral-600 bg-white/5 px-1.5 py-0.5 rounded">{{ barIntervalDescription }}</span>
          </div>
        </div>
      </div>

      <!-- Controls -->
      <div class="flex flex-col sm:flex-row gap-2 sm:gap-3 sm:items-center">
        <!-- Period Tabs -->
        <div class="flex items-center bg-white/5 rounded-lg p-1 overflow-x-auto">
          <button
            v-for="period in periods.filter(p => !p.isCustom)"
            :key="period.value"
            @click="selectPeriod(period)"
            :class="[
              'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors whitespace-nowrap flex-shrink-0',
              selectedPeriod === period.value && !customTimeframeActive
                ? 'bg-neutral-800 text-white shadow-sm border border-white/10'
                : 'text-neutral-500 hover:text-white'
            ]"
          >
            {{ period.label }}
          </button>
          
          <!-- Custom Date Range Picker (inside Period Tabs) -->
          <div class="custom-date-picker-wrapper flex-shrink-0">
            <DateRangePicker
              v-model="customDateRange"
              :enable-time-picker="customPrecision.enableTime"
              :custom-timeframe-active="customTimeframeActive"
              :custom-timeframe-label="customTimeframeLabel"
              :custom-precision="customPrecision"
              @update:model-value="onDateRangeChange"
            />
          </div>
        </div>

        <div class="hidden sm:block w-px h-4 bg-white/10"></div>

        <!-- Metric Tabs -->
        <div class="flex items-center bg-white/5 rounded-lg p-1 overflow-x-auto">
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

<style>
/* Vue DatePicker Override for App Theme */
.custom-date-picker-wrapper {
  --dp-font-family: inherit;
  --dp-border-radius: 8px; /* rounded-lg */
  --dp-cell-border-radius: 6px;
  
  /* Dark theme overrides */
  --dp-background-color: #171717; /* neutral-900 */
  --dp-text-color: #e5e5e5; /* neutral-200 */
  --dp-hover-color: rgba(255, 255, 255, 0.05);
  --dp-hover-text-color: #ffffff;
  --dp-hover-icon-color: #ffffff;
  --dp-primary-color: #10b981; /* emerald-500 */
  --dp-primary-disabled-color: #059669; /* emerald-600 */
  --dp-primary-text-color: #ffffff;
  --dp-secondary-color: #525252; /* neutral-600 */
  --dp-border-color: rgba(255, 255, 255, 0.1);
  --dp-menu-border-color: rgba(255, 255, 255, 0.1);
  --dp-border-color-hover: rgba(255, 255, 255, 0.2);
  --dp-disabled-color: #404040; /* neutral-700 */
  --dp-scroll-bar-background: #262626; /* neutral-800 */
  --dp-scroll-bar-color: #525252; /* neutral-600 */
  --dp-success-color: #10b981; /* emerald-500 */
  --dp-icon-color: #a3a3a3; /* neutral-400 */
  --dp-danger-color: #ef4444; /* red-500 */
  --dp-marker-color: #10b981; /* emerald-500 */
  --dp-tooltip-color: #262626; /* neutral-800 */
}

/* Adjust input wrapper to match other buttons */
.custom-date-picker-wrapper .dp__input_wrap {
  display: flex;
}

/* Remove default border from input since we use a custom trigger */
.custom-date-picker-wrapper .dp__main {
  font-family: inherit;
}

/* 
 * GLOBAL STYLES for teleported datepicker menu
 * The menu is teleported to body, so we need global selectors
 */

/* Dark theme for all dp elements */
.dp__theme_dark {
  --dp-background-color: #0A0A0A !important;
  --dp-text-color: #e5e5e5 !important;
  --dp-hover-color: rgba(255, 255, 255, 0.08) !important;
  --dp-hover-text-color: #ffffff !important;
  --dp-hover-icon-color: #ffffff !important;
  --dp-primary-color: #10b981 !important;
  --dp-primary-disabled-color: #059669 !important;
  --dp-primary-text-color: #ffffff !important;
  --dp-secondary-color: #525252 !important;
  --dp-border-color: rgba(255, 255, 255, 0.15) !important;
  --dp-menu-border-color: rgba(255, 255, 255, 0.15) !important;
  --dp-border-color-hover: rgba(255, 255, 255, 0.25) !important;
  --dp-disabled-color: #404040 !important;
  --dp-scroll-bar-background: #262626 !important;
  --dp-scroll-bar-color: #525252 !important;
  --dp-success-color: #10b981 !important;
  --dp-icon-color: #a3a3a3 !important;
  --dp-danger-color: #ef4444 !important;
  --dp-marker-color: #10b981 !important;
  --dp-tooltip-color: #262626 !important;
  --dp-font-family: inherit !important;
  --dp-border-radius: 8px !important;
  --dp-cell-border-radius: 6px !important;
}

/* Menu container */
.dp__menu {
  background-color: #0A0A0A !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  box-shadow: 0 10px 40px -10px rgba(0, 0, 0, 0.7) !important;
  border-radius: 12px !important;
  font-family: inherit !important;
}

/* Calendar cells */
.dp__calendar_item {
  font-size: 13px !important;
}

.dp__cell_inner {
  border-radius: 6px !important;
}

/* Month/Year header */
.dp__month_year_wrap {
  gap: 8px !important;
}

.dp__month_year_select {
  color: #e5e5e5 !important;
  font-weight: 500 !important;
}

.dp__month_year_select:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
  color: #ffffff !important;
}

/* Navigation arrows */
.dp__arrow_top,
.dp__arrow_bottom {
  border-color: rgba(255, 255, 255, 0.15) !important;
  background-color: #0A0A0A !important;
}

.dp__inner_nav {
  color: #a3a3a3 !important;
}

.dp__inner_nav:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
  color: #ffffff !important;
}

/* Range selection styling */
.dp__range_start,
.dp__range_end {
  background-color: #10b981 !important;
  color: #ffffff !important;
}

.dp__range_between {
  background-color: rgba(16, 185, 129, 0.2) !important;
  color: #e5e5e5 !important;
}

/* Sidebar adjustments */
.dp__sidebar_left {
  background-color: rgba(255, 255, 255, 0.02) !important;
  border-right: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 0 !important;
}

/* =====================
   HIDE TIME PICKER WRAPPER (above calendars)
   ===================== */
.dp--tp-wrap {
  display: none !important;
}

/* =====================
   TIME PICKER STYLES 
   ===================== */

/* Time picker container */
.dp__time_picker_overlay_container {
  background-color: #0A0A0A !important;
}

/* Time picker wrapper */
.dp__overlay_container {
  background-color: #0A0A0A !important;
}

/* Time display button in calendar header */
.dp__time_display {
  color: #ffffff !important;
  background-color: rgba(16, 185, 129, 0.15) !important;
  border: 1px solid rgba(16, 185, 129, 0.3) !important;
  border-radius: 6px !important;
  padding: 6px 12px !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace !important;
}

.dp__time_display:hover {
  background-color: rgba(16, 185, 129, 0.25) !important;
  border-color: rgba(16, 185, 129, 0.5) !important;
}

/* Time picker panel */
.dp__time_picker_inline_container {
  background-color: #0A0A0A !important;
  padding: 16px !important;
}

/* Time columns container */
.dp__time_col {
  padding: 0 4px !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  gap: 4px !important;
}

.dp__time_col_reg {
  color: #e5e5e5 !important;
}

/* Time value display */
.dp__time_input {
  font-size: 24px !important;
  font-weight: 700 !important;
  color: #ffffff !important;
  background-color: rgba(255, 255, 255, 0.08) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  border-radius: 8px !important;
  padding: 12px 8px !important;
  min-width: 56px !important;
  text-align: center !important;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace !important;
}

.dp__time_input:hover {
  background-color: rgba(255, 255, 255, 0.12) !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
}

/* Colon separator between hours and minutes */
.dp__tp_inline_btn_bar {
  display: none !important;
}

/* Time separator (:) */
.dp__time_col_sec {
  font-size: 24px !important;
  font-weight: 700 !important;
  color: #525252 !important;
  padding: 0 2px !important;
}

/* Increment button (up arrow / +) */
.dp__inc_dec_button {
  width: 36px !important;
  height: 28px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  color: #a3a3a3 !important;
  background-color: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 6px !important;
  margin: 0 !important;
  cursor: pointer !important;
  transition: all 0.15s ease !important;
}

.dp__inc_dec_button:hover {
  background-color: rgba(16, 185, 129, 0.2) !important;
  border-color: rgba(16, 185, 129, 0.4) !important;
  color: #10b981 !important;
}

.dp__inc_dec_button:active {
  background-color: rgba(16, 185, 129, 0.3) !important;
  transform: scale(0.95) !important;
}

.dp__inc_dec_button svg {
  width: 14px !important;
  height: 14px !important;
}

/* Time picker overlay wrapper */
.dp__overlay {
  background-color: rgba(0, 0, 0, 0.8) !important;
}

/* Time picker AM/PM (if used) */
.dp__pm_am_button {
  background-color: rgba(255, 255, 255, 0.08) !important;
  color: #e5e5e5 !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  border-radius: 6px !important;
  padding: 8px 12px !important;
  font-weight: 500 !important;
}

.dp__pm_am_button:hover {
  background-color: rgba(255, 255, 255, 0.12) !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
}

/* =====================
   OFFSET DATES (Previous/Next month dates)
   ===================== */

/* Dates from previous/next month - more visible */
.dp__cell_offset {
  color: #525252 !important;
}

/* When offset date is selected - make it clearly visible */
.dp__cell_offset.dp__range_start,
.dp__cell_offset.dp__range_end,
.dp__cell_offset.dp__active_date {
  background-color: #10b981 !important;
  color: #ffffff !important;
}

.dp__cell_offset.dp__range_between {
  background-color: rgba(16, 185, 129, 0.2) !important;
  color: #a3a3a3 !important;
}

/* =====================
   ACTION ROW & BUTTONS
   ===================== */
.dp__action_row {
  border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.dp__action_buttons {
  padding: 12px !important;
  gap: 8px !important;
}

.dp__action_select {
  background: linear-gradient(to right, #10b981, #14b8a6) !important;
  border: none !important;
  font-weight: 500 !important;
  color: #ffffff !important;
  border-radius: 6px !important;
  padding: 8px 16px !important;
}

.dp__action_select:hover {
  opacity: 0.9 !important;
}

.dp__action_cancel {
  color: #a3a3a3 !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 6px !important;
  padding: 8px 16px !important;
  background-color: transparent !important;
}

.dp__action_cancel:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

/* =====================
   OVERLAY (month/year picker)
   ===================== */
.dp__overlay {
  background-color: #0A0A0A !important;
}

.dp__overlay_cell {
  color: #e5e5e5 !important;
}

.dp__overlay_cell:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
}

.dp__overlay_cell_active {
  background-color: #10b981 !important;
  color: #ffffff !important;
}

/* =====================
   TODAY INDICATOR
   ===================== */
.dp__today {
  border-color: #10b981 !important;
}

/* =====================
   DISABLED DATES
   ===================== */
.dp__cell_disabled {
  color: #404040 !important;
  cursor: not-allowed !important;
}
</style>
