<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { fundingRateApi } from '../services/api'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js'
import { Line } from 'vue-chartjs'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
)

const props = defineProps({
  symbol: {
    type: String,
    required: true
  }
})

const loading = ref(true)
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

const fetchData = async () => {
  loading.value = true
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
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
  }
}

watch(() => [props.symbol, selectedDex.value], fetchData, { immediate: true })

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
            backgroundColor: color,
            data: metricsData,
            tension: 0.4,
            spanGaps: true,
            pointRadius: 2,
            borderWidth: 2
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

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: { color: '#9CA3AF' }
    },
    tooltip: {
      mode: 'index',
      intersect: false
    }
  },
  scales: {
    y: {
      grid: { color: 'rgba(255, 255, 255, 0.1)' },
      ticks: { color: '#9CA3AF' }
    },
    x: {
      grid: { display: false },
      ticks: { 
        color: '#9CA3AF',
        maxTicksLimit: 12 // Limit number of x-axis labels for readability
      }
    }
  }
}
</script>

<template>
  <div class="glass-card rounded-3xl p-6">
    <div class="flex flex-col sm:flex-row items-center justify-between mb-6 gap-4">
      <h3 class="text-lg font-medium text-white">Funding Rate History</h3>
      
      <div class="flex flex-wrap gap-2">
        <!-- DEX Tabs -->
        <div class="bg-white/5 rounded-lg p-1 flex">
          <button 
            v-for="dex in dexes" 
            :key="dex"
            @click="selectedDex = dex"
            :class="[
              'px-3 py-1 text-xs font-medium rounded-md transition-all',
              selectedDex === dex ? 'bg-indigo-600 text-white shadow-sm' : 'text-neutral-400 hover:text-white'
            ]"
          >
            {{ dex }}
          </button>
        </div>

        <!-- Metric Tabs -->
        <div class="bg-white/5 rounded-lg p-1 flex">
          <button 
            v-for="metric in metrics" 
            :key="metric.value"
            @click="selectedMetric = metric.value"
            :class="[
              'px-3 py-1 text-xs font-medium rounded-md transition-all',
              selectedMetric === metric.value ? 'bg-indigo-600 text-white shadow-sm' : 'text-neutral-400 hover:text-white'
            ]"
          >
            {{ metric.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Chart -->
    <div class="h-[300px] w-full">
      <Line :data="unifiedChartData" :options="chartOptions" />
    </div>
  </div>
</template>
