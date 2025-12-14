<script setup>
import { RouterLink, useRoute } from 'vue-router'
import { computed } from 'vue'
import { useAuth } from '../stores/auth'

const route = useRoute()
const { state } = useAuth()

const navItems = computed(() => {
  const items = [
    { path: '/overview', name: 'Overview', icon: 'lucide:layout-template' },
    { path: '/arbitrage', name: 'Opportunities', icon: 'lucide:zap' },
    { path: '/swap', name: 'Swap', icon: 'lucide:repeat' },
    { path: '/learn', name: 'Learn', icon: 'lucide:graduation-cap' },
  ]
  // Réactif: vérifie le state directement
  if (state.user?.role === 'ADMIN') {
    items.push({ path: '/admin', name: 'Administration', icon: 'lucide:shield' })
  }
  return items
})
</script>

<template>
  <aside class="hidden md:flex flex-col w-20 lg:w-52 h-full z-10 glass border-r-0">
    <!-- Logo -->
    <div class="p-6 mb-6">
      <RouterLink to="/" class="flex items-center gap-3 hover:opacity-80 transition-opacity cursor-pointer">
        <img src="@/assets/logo.png" alt="CactusFund Logo" class="w-8 h-8 rounded-xl shadow-lg shadow-green-500/20" />
        <span class="hidden lg:block text-sm font-semibold tracking-tight text-white">CactusFund</span>
      </RouterLink>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 px-4 space-y-1">
      <RouterLink 
        v-for="item in navItems" 
        :key="item.path"
        :to="item.path"
        :class="[
          'group flex items-center gap-3 px-3 py-3 text-sm rounded-xl transition-colors border',
          route.path === item.path 
            ? 'text-white bg-white/5 border-white/5 shadow-sm hover:shadow-md hover:shadow-emerald-500/10' 
            : 'text-neutral-400 border-transparent hover:text-white hover:bg-white/5'
        ]"
      >
        <span class="iconify" :class="route.path === item.path ? 'text-white' : ''" :data-icon="item.icon" data-width="18" data-stroke-width="1.5"></span>
        <span class="hidden lg:block" :class="route.path === item.path ? 'font-medium' : ''">{{ item.name }}</span>
      </RouterLink>
    </nav>

    <!-- Powered by Cactus - GitHub Link -->
    <div class="p-4 mt-auto">
      <a 
        href="https://github.com/cactuszdre" 
        target="_blank"
        rel="noopener noreferrer"
        class="glass-card p-3 rounded-2xl flex items-center gap-3 cursor-pointer hover:bg-white/5 transition-all duration-300 group hover:ring-1 hover:ring-white/10"
      >
        <!-- Premium Logo Design -->
        <div class="relative w-8 h-8 rounded-xl bg-gradient-to-br from-neutral-800 to-neutral-900 border border-white/10 flex items-center justify-center shadow-lg overflow-hidden group-hover:border-white/20 transition-all">
          <!-- Subtle shine effect -->
          <div class="absolute inset-0 bg-gradient-to-br from-white/10 via-transparent to-transparent opacity-50"></div>
          <!-- Letter C -->
          <span class="relative text-white text-xs font-bold tracking-tight">C</span>
          <!-- GitHub icon on hover -->
          <div class="absolute inset-0 bg-neutral-900/90 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
            <svg class="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
          </div>
        </div>
        <div class="hidden lg:flex flex-col">
          <span class="text-xs font-medium text-white group-hover:text-neutral-200 transition-colors">Powered By</span>
          <span class="text-[10px] text-neutral-500 group-hover:text-neutral-400 transition-colors flex items-center gap-1">
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            @cactuszdre
          </span>
        </div>
      </a>
    </div>
  </aside>
</template>
