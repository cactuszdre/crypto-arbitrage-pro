<script setup>
// This component wraps VueDatePicker for client-side only rendering
import { VueDatePicker } from '@vuepic/vue-datepicker'
import '@vuepic/vue-datepicker/dist/main.css'
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: null
  },
  customTimeframeActive: {
    type: Boolean,
    default: false
  },
  customTimeframeLabel: {
    type: String,
    default: ''
  },
  customPrecision: {
    type: Object,
    default: () => ({ label: 'Select dates', interval: null, description: '' })
  }
})

const emit = defineEmits(['update:modelValue'])

// Time values
const fromHour = ref(0)
const fromMinute = ref(0)
const toHour = ref(23)
const toMinute = ref(59)

// Check if a date is today (UTC)
const isToday = (date) => {
  if (!date) return false
  const d = new Date(date)
  const now = new Date()
  return d.getUTCFullYear() === now.getUTCFullYear() &&
         d.getUTCMonth() === now.getUTCMonth() &&
         d.getUTCDate() === now.getUTCDate()
}

// Sync times from props
watch(() => props.modelValue, (val) => {
  if (val && val[0] && val[1]) {
    const from = new Date(val[0])
    const to = new Date(val[1])
    fromHour.value = from.getUTCHours()
    fromMinute.value = from.getUTCMinutes()
    toHour.value = to.getUTCHours()
    toMinute.value = to.getUTCMinutes()
  }
}, { immediate: true })

// Handle date selection from VueDatePicker
const onDateChange = (range) => {
  if (!range || !range[0] || !range[1]) {
    emit('update:modelValue', range)
    return
  }
  
  // Create new dates with our time values
  const from = new Date(range[0])
  const to = new Date(range[1])
  
  // If "to" date is today, set time to current UTC time
  if (isToday(to)) {
    const now = new Date()
    toHour.value = now.getUTCHours()
    toMinute.value = now.getUTCMinutes()
  }
  
  from.setUTCHours(fromHour.value, fromMinute.value, 0, 0)
  to.setUTCHours(toHour.value, toMinute.value, 0, 0)
  
  emit('update:modelValue', [from, to])
}

// Update time and re-emit
const updateTime = () => {
  if (props.modelValue && props.modelValue[0] && props.modelValue[1]) {
    const from = new Date(props.modelValue[0])
    const to = new Date(props.modelValue[1])
    
    from.setUTCHours(fromHour.value, fromMinute.value, 0, 0)
    to.setUTCHours(toHour.value, toMinute.value, 0, 0)
    
    emit('update:modelValue', [from, to])
  }
}

const formatDate = (date) => {
  if (!date) return '--'
  const d = new Date(date)
  return d.toLocaleDateString('en-US', { 
    day: '2-digit', 
    month: 'short',
    timeZone: 'UTC'
  })
}

// Time change helpers
const changeFromHour = (delta) => { fromHour.value = (fromHour.value + delta + 24) % 24; updateTime() }
const changeFromMinute = (delta) => { fromMinute.value = (fromMinute.value + delta + 60) % 60; updateTime() }
const changeToHour = (delta) => { toHour.value = (toHour.value + delta + 24) % 24; updateTime() }
const changeToMinute = (delta) => { toMinute.value = (toMinute.value + delta + 60) % 60; updateTime() }

const setFromHour = (e) => { fromHour.value = Math.max(0, Math.min(23, Number(e.target.value))); updateTime() }
const setFromMinute = (e) => { fromMinute.value = Math.max(0, Math.min(59, Number(e.target.value))); updateTime() }
const setToHour = (e) => { toHour.value = Math.max(0, Math.min(23, Number(e.target.value))); updateTime() }
const setToMinute = (e) => { toMinute.value = Math.max(0, Math.min(59, Number(e.target.value))); updateTime() }
</script>

<template>
  <div class="date-range-picker-wrapper">
    <VueDatePicker
      :model-value="modelValue"
      range
      multi-calendars
      :enable-time-picker="false"
      :max-date="new Date()"
      :dark="true"
      :auto-apply="false"
      :close-on-scroll="false"
      :hide-offset-dates="true"
      utc
      timezone="UTC"
      format="dd/MM/yy"
      preview-format="dd MMM yyyy"
      placeholder="Custom range"
      @update:model-value="onDateChange"
      :teleport="true"
    >
      <template #trigger>
        <button
          :class="[
            'px-2.5 sm:px-3 py-1.5 rounded-md text-[11px] sm:text-xs font-medium transition-colors whitespace-nowrap flex-shrink-0 flex items-center gap-1.5',
            customTimeframeActive
              ? 'bg-neutral-800 text-white shadow-sm border border-white/10'
              : 'text-neutral-500 hover:text-white'
          ]"
        >
          <svg class="w-3 h-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
          </svg>
          <span v-if="customTimeframeActive" class="truncate max-w-[120px]">{{ customTimeframeLabel }}</span>
          <span v-else>Custom</span>
        </button>
      </template>
      
      <!-- Integrated Action Preview with Inline Time Controls -->
      <template #action-preview="{ value }">
        <div class="inline-time-container">
          <!-- Labels Row -->
          <div class="labels-row">
            <div class="label-group">
              <span class="label-title">FROM</span>
              <span class="label-date">{{ value && value[0] ? formatDate(value[0]) : '--' }}</span>
            </div>
            
            <!-- Spacer to align visually with inputs -->
            <div class="label-spacer"></div>

            <div class="label-group">
              <span class="label-title">TO</span>
              <span class="label-date">{{ value && value[1] ? formatDate(value[1]) : '--' }}</span>
            </div>
          </div>

          <!-- Inputs Row -->
          <div class="inputs-row">
            <!-- FROM Spinner -->
            <div class="time-spinner">
              <div class="spinner-col">
                <button class="spin-btn" @click.stop.prevent="changeFromHour(1)">▲</button>
                <input type="number" :value="fromHour" @input="setFromHour" @click.stop min="0" max="23" />
                <button class="spin-btn" @click.stop.prevent="changeFromHour(-1)">▼</button>
              </div>
              <span class="colon">:</span>
              <div class="spinner-col">
                <button class="spin-btn" @click.stop.prevent="changeFromMinute(5)">▲</button>
                <input type="number" :value="fromMinute" @input="setFromMinute" @click.stop min="0" max="59" />
                <button class="spin-btn" @click.stop.prevent="changeFromMinute(-5)">▼</button>
              </div>
            </div>

            <!-- Centered Arrow -->
            <div class="separator">→</div>

            <!-- TO Spinner -->
            <div class="time-spinner">
              <div class="spinner-col">
                <button class="spin-btn" @click.stop.prevent="changeToHour(1)">▲</button>
                <input type="number" :value="toHour" @input="setToHour" @click.stop min="0" max="23" />
                <button class="spin-btn" @click.stop.prevent="changeToHour(-1)">▼</button>
              </div>
              <span class="colon">:</span>
              <div class="spinner-col">
                <button class="spin-btn" @click.stop.prevent="changeToMinute(5)">▲</button>
                <input type="number" :value="toMinute" @input="setToMinute" @click.stop min="0" max="59" />
                <button class="spin-btn" @click.stop.prevent="changeToMinute(-5)">▼</button>
              </div>
            </div>
          </div>
        </div>
      </template>
      
      <!-- Precision indicator in sidebar -->
      <template #left-sidebar>
        <div class="p-3 space-y-3">
          <div class="text-xs text-neutral-400 font-medium">Data Precision</div>
          <div 
            class="p-2 rounded-lg text-xs"
            :class="{
              'bg-emerald-500/20 text-emerald-400': customPrecision.interval === '5min',
              'bg-cyan-500/20 text-cyan-400': customPrecision.interval === 'hourly',
              'bg-amber-500/20 text-amber-400': customPrecision.interval === 'daily',
              'bg-neutral-500/20 text-neutral-400': !customPrecision.interval
            }"
          >
            <div class="font-medium">{{ customPrecision.label }}</div>
            <div class="text-[10px] opacity-70 mt-0.5">{{ customPrecision.description }}</div>
          </div>
          <div class="text-[10px] text-neutral-500 flex items-center gap-1">
            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
            UTC timezone
          </div>
        </div>
      </template>
    </VueDatePicker>
  </div>
</template>

<style scoped>
.inline-time-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 0;
}

.labels-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4px; /* match spinner padding visually */
}

.label-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 60px;
}

/* Align second label to right to match right spinner */
.label-group:last-child {
  align-items: flex-end;
}

.label-spacer {
  flex-grow: 1;
}

.label-title {
  font-size: 9px;
  font-weight: 700;
  color: #525252;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.label-date {
  font-size: 11px;
  font-weight: 600;
  color: #e5e5e5;
}

.inputs-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.separator {
  color: #525252;
  font-size: 14px;
  margin-top: -2px; /* Visual optical alignment */
}

.time-spinner {
  display: flex;
  align-items: center;
  gap: 2px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px; /* Slightly more rounded */
  padding: 3px 6px;
}

.spinner-col {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.spin-btn {
  width: 20px;
  height: 10px; /* Reduced height */
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 8px; /* Slightly larger icon */
  color: #525252;
  cursor: pointer;
  background: transparent;
  border: none;
  transition: all 0.15s;
  opacity: 0.6;
}

.spin-btn:hover {
  color: #10b981;
  opacity: 1;
  background: rgba(16, 185, 129, 0.05);
}

.spinner-col input {
  width: 20px;
  height: 18px;
  background: transparent;
  border: none;
  text-align: center;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: #ffffff;
  padding: 0;
  margin: 1px 0;
  -moz-appearance: textfield;
}

.spinner-col input::-webkit-outer-spin-button,
.spinner-col input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.colon {
  font-size: 13px;
  font-weight: 700;
  color: #404040;
  margin-bottom: 1px;
}
</style>
