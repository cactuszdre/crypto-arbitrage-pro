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
const selectedDex = ref('All') // 'All', 'Paradex', 'Lighter'
const selectedMetric = ref('rateAnnual') // 'rate', 'rate8h', 'rateAnnual'

const metrics = [
  { label: '1H Rate', value: 'rate' },
  { label: '8H Rate', value: 'rate8h' },
  { label: 'Annualized', value: 'rateAnnual' }
]

const dexes = ['All', 'Paradex', 'Lighter']

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
          symbol: props.symbol.replace('-USD-PERP', '-USD-PERP') , // Ensure matching logic if needed or just pass symbol
          hours: 24
        })
        data.push(...lResponse.data.map(d => ({ ...d, dex: 'Lighter' })))
       } catch (e) {
         console.error("No Lighter data", e)
       }
    }

    // Sort by timestamp asc
    historyData.value = data.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
  }
}

watch(() => [props.symbol, selectedDex.value], fetchData, { immediate: true })

const chartData = computed(() => {
  // Extract unique timestamps for X-axis
  const timestamps = [...new Set(historyData.value.map(d => new Date(d.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})))]
  
  // Prepare datasets
  const datasets = []
  
  if (selectedDex.value === 'All' || selectedDex.value === 'Paradex') {
    const paradexData = historyData.value.filter(d => d.dex === 'Paradex')
    // Map data to time slots - simplified for now, assuming strictly aligned data or using points
    datasets.push({
      label: 'Paradex',
      borderColor: '#10B981', // Emerald
      backgroundColor: '#10B981',
      data: paradexData.map(d => ({ x: new Date(d.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), y: d[selectedMetric.value] * (selectedMetric.value === 'rate' ? 100 : 1) })), // Convert decimal to % for 'rate' if needed, but rateAnnual/rate8h are already sensible
      tension: 0.4
    })
  }

  if (selectedDex.value === 'All' || selectedDex.value === 'Lighter') {
    const lighterData = historyData.value.filter(d => d.dex === 'Lighter')
    datasets.push({
      label: 'Lighter',
      borderColor: '#6366F1', // Indigo
      backgroundColor: '#6366F1',
      data: lighterData.map(d => ({ x: new Date(d.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), y: d[selectedMetric.value] * (selectedMetric.value === 'rate' ? 100 : 1) })),
      tension: 0.4
    })
  }

  return {
    labels: timestamps, // This might be misaligned if timestamps differ slightly between DEXes. Better to use scatter or flexible time scale?
    // For simplicity, let's use the datasets 'x' value parsing if we used 'time' scale, but here we use Category scale.
    // To make it simple: We just ignore the labels prop for a moment and let Chart.js handle it if we passed structure data?
    // Actually, Line chart expects labels array matching the data indices for Category scale.
    // To properly support multi-series with different timestamps, we should use 'time' scale, but that requires an adapter.
    // Alternative: Just just map all unique sorted timestamps as labels and fill gaps with null.
    datasets
  }
})

// Better approach for labels: Unified Timeline
const unifiedChartData = computed(() => {
    // 1. Collect all unique timestamps, sorted
    const allTimestamps = [...new Set(historyData.value.map(d => d.timestamp))].sort()
    
    // 2. Create labels
    const labels = allTimestamps.map(t => new Date(t).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}))
    
    // 3. Map data to these timestamps
    const getDataset = (dexName, color) => {
        const metricsData = allTimestamps.map(t => {
            const entry = historyData.value.find(d => d.dex === dexName && d.timestamp === t)
            // Correction: If 'rate' (1h raw), multi by 100 to show %.
            // If 'rateAnnual', it's already % in backend? DB has rateAnnual as e.g. 10.50 (percentage value).
            // Let's assume rateAnnual is %, rate is decimal.
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
            spanGaps: true // Connect lines if data is missing for some timestamps
        }
    }
    
    const datasets = []
    if (selectedDex.value === 'All' || selectedDex.value === 'Paradex') {
        datasets.push(getDataset('Paradex', '#10B981'))
    }
    if (selectedDex.value === 'All' || selectedDex.value === 'Lighter') {
        datasets.push(getDataset('Lighter', '#6366F1'))
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
      ticks: { color: '#9CA3AF' }
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
