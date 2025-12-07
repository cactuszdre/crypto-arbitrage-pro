<script setup>
import { RouterLink, useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const user = JSON.parse(localStorage.getItem('user') || '{}')

const navItems = computed(() => {
  const items = [
    { path: '/', name: 'Overview', icon: 'lucide:layout-template' },
    { path: '/history', name: 'Market Flow', icon: 'lucide:waves' },
    { path: '/arbitrage', name: 'Opportunities', icon: 'lucide:zap' },
  ]
  if (user.role === 'ADMIN') {
    items.push({ path: '/admin', name: 'Administration', icon: 'lucide:shield' })
  }
  return items
})
</script>

<template>
  <aside class="hidden md:flex flex-col w-20 lg:w-64 h-full z-10 glass border-r-0">
    <!-- Logo -->
    <div class="p-6 mb-2">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-xl bg-white text-black flex items-center justify-center shadow-lg shadow-white/5">
          <span class="iconify" data-icon="lucide:infinity" data-width="16"></span>
        </div>
        <span class="hidden lg:block text-sm font-semibold tracking-tight text-white">CactusFund</span>
      </div>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 px-4 space-y-2">
      <RouterLink 
        v-for="item in navItems" 
        :key="item.path"
        :to="item.path"
        :class="[
          'group flex items-center gap-3 px-3 py-3 text-sm rounded-xl transition-colors border',
          route.path === item.path 
            ? 'text-white bg-white/5 border-white/5 shadow-sm hover:shadow-md hover:shadow-indigo-500/10' 
            : 'text-neutral-400 border-transparent hover:text-white hover:bg-white/5'
        ]"
      >
        <span class="iconify" :class="route.path === item.path ? 'text-white' : ''" :data-icon="item.icon" data-width="18" data-stroke-width="1.5"></span>
        <span class="hidden lg:block" :class="route.path === item.path ? 'font-medium' : ''">{{ item.name }}</span>
      </RouterLink>
    </nav>

    <!-- User Profile -->
    <div class="p-4 mt-auto">
      <div class="glass-card p-3 rounded-2xl flex items-center gap-3 cursor-pointer hover:bg-white/5 transition-colors">
        <div class="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white text-xs font-bold">
          C
        </div>
        <div class="hidden lg:flex flex-col">
          <span class="text-xs font-medium text-white">Crypto Trader</span>
          <span class="text-[10px] text-neutral-500">Pro Plan</span>
        </div>
      </div>
    </div>
  </aside>
</template>
