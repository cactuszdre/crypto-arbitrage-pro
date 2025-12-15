<script setup>
import { ref } from 'vue'
import { RouterView, RouterLink, useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import { useAuth } from './stores/auth'

const route = useRoute()
const { state } = useAuth()

// Mobile menu state
const mobileMenuOpen = ref(false)

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}

// Nav items (same as Sidebar)
const navItems = [
  { path: '/overview', name: 'Asset Details', icon: 'lucide:bar-chart-3' },
  { path: '/arbitrage', name: 'Opportunities', icon: 'lucide:zap' },
  { path: '/swap', name: 'Swap', icon: 'lucide:repeat' },
  { path: '/learn', name: 'Learn', icon: 'lucide:graduation-cap' },
]
</script>

<template>
  <div class="flex w-full h-full relative">
    <!-- Animated Background Blobs -->
    <div class="fixed inset-0 z-0 overflow-hidden pointer-events-none">
      <div class="absolute top-0 left-1/4 w-96 h-96 bg-indigo-500/20 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob"></div>
      <div class="absolute top-0 right-1/4 w-96 h-96 bg-purple-500/20 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob animation-delay-2000"></div>
      <div class="absolute -bottom-32 left-1/3 w-96 h-96 bg-emerald-500/10 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob animation-delay-4000"></div>
    </div>

    <!-- Mobile Floating Menu Button (bottom left) - Visible on Mobile OR Home Page -->
    <div v-if="route.path !== '/login'" class="fixed bottom-6 left-6 z-50" :class="{ 'md:hidden': route.path !== '/' }">
      <!-- Menu Popup -->
      <div
        v-show="mobileMenuOpen"
        class="absolute bottom-16 left-0 w-56 glass rounded-2xl shadow-2xl border border-white/10 overflow-hidden mb-2"
      >
        <!-- Logo Header -->
        <RouterLink 
          to="/" 
          @click="closeMobileMenu"
          class="flex items-center gap-3 p-4 border-b border-white/10 hover:bg-white/5 transition-colors"
        >
          <img src="@/assets/logo.png" alt="CactusFund Logo" class="w-8 h-8 rounded-xl shadow-lg shadow-green-500/20" />
          <span class="text-sm font-semibold tracking-tight text-white">CactusFund</span>
        </RouterLink>

        <!-- Navigation -->
        <nav class="p-2 space-y-1">
          <RouterLink
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            @click="closeMobileMenu"
            :class="[
              'flex items-center gap-3 px-3 py-2.5 text-sm rounded-xl transition-all touch-manipulation active:scale-95',
              route.path === item.path
                ? 'text-white bg-gradient-to-r from-emerald-500/20 to-green-500/20 border border-emerald-500/30'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            ]"
          >
            <span class="iconify" :data-icon="item.icon" data-width="18"></span>
            <span>{{ item.name }}</span>
          </RouterLink>

          <!-- Admin Link (conditionally) -->
          <RouterLink
            v-if="state.user?.role === 'ADMIN'"
            to="/admin"
            @click="closeMobileMenu"
            :class="[
              'flex items-center gap-3 px-3 py-2.5 text-sm rounded-xl transition-all touch-manipulation active:scale-95',
              route.path === '/admin'
                ? 'text-white bg-gradient-to-r from-emerald-500/20 to-green-500/20 border border-emerald-500/30'
                : 'text-neutral-400 hover:text-white hover:bg-white/5'
            ]"
          >
            <span class="iconify" data-icon="lucide:shield" data-width="18"></span>
            <span>Administration</span>
          </RouterLink>
        </nav>
      </div>

      <!-- Floating Action Button -->
      <button
        @click="toggleMobileMenu"
        type="button"
        :class="[
          'w-14 h-14 rounded-full flex items-center justify-center shadow-lg transition-all duration-300 touch-manipulation active:scale-90',
          mobileMenuOpen
            ? 'bg-red-500 shadow-red-500/30 rotate-45'
            : 'bg-gradient-to-br from-emerald-500 to-green-600 shadow-emerald-500/30'
        ]"
      >
        <span class="iconify text-white" :data-icon="mobileMenuOpen ? 'lucide:plus' : 'lucide:menu'" data-width="24"></span>
      </button>
    </div>

    <!-- Mobile Menu Overlay - Visible on Mobile OR Home Page when open -->
    <div
      v-if="mobileMenuOpen"
      class="fixed inset-0 bg-black/40 z-40"
      :class="{ 'md:hidden': route.path !== '/' }"
      @click="closeMobileMenu"
    ></div>

    <!-- Sidebar (desktop only) - Hidden on Home Page and Login -->
    <Sidebar v-if="route.path !== '/login' && route.path !== '/'" />

    <!-- Main Content -->
    <main class="flex-1 flex flex-col h-full overflow-hidden relative z-10">
      <Header v-if="route.path !== '/login' && route.path !== '/'" />
      <div id="main-scroll-container" :class="['flex-1 overflow-y-auto', (route.path !== '/login' && route.path !== '/') ? 'px-4 sm:px-6 lg:px-8 pt-4 sm:pt-6 pb-24 md:pb-8 space-y-4 sm:space-y-6' : '']">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style>
/* Floating button animation */
.rotate-45 {
  transform: rotate(45deg);
}
</style>
