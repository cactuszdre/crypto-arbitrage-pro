<script setup>
defineProps({
  title: String,
  value: [String, Number],
  icon: String,
  badge: String,
  badgeType: {
    type: String,
    default: 'success' // 'success', 'warning', 'danger'
  }
})

const badgeClasses = {
  success: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/10',
  warning: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/10',
  danger: 'bg-red-500/10 text-red-400 border-red-500/10'
}
</script>

<template>
  <div class="glass-card p-6 rounded-3xl relative group overflow-hidden">
    <div class="absolute top-0 right-0 p-6 opacity-50 group-hover:opacity-100 transition-opacity">
      <span 
        class="iconify text-white/10 group-hover:text-indigo-500/20 transform group-hover:scale-110 transition-all duration-500" 
        :data-icon="icon || 'lucide:activity'" 
        data-width="64"
      ></span>
    </div>
    <div class="relative z-10">
      <p class="text-xs font-medium text-neutral-400 uppercase tracking-wide">{{ title }}</p>
      <h2 class="text-3xl font-semibold text-white mt-2 tracking-tight">{{ value }}</h2>
      <div v-if="badge" class="flex items-center gap-2 mt-4">
        <span :class="['text-[10px] font-medium px-2 py-1 rounded-full border', badgeClasses[badgeType]]">
          {{ badge }}
        </span>
      </div>
      <slot name="footer"></slot>
    </div>
  </div>
</template>
