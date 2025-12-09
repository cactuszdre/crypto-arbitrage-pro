<script setup>
import { RouterView } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
</script>

<template>
  <div class="flex w-full h-full relative">
    <!-- Animated Background Blobs -->
    <div class="fixed inset-0 z-0 overflow-hidden pointer-events-none">
      <div class="absolute top-0 left-1/4 w-96 h-96 bg-indigo-500/20 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob"></div>
      <div class="absolute top-0 right-1/4 w-96 h-96 bg-purple-500/20 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob animation-delay-2000"></div>
      <div class="absolute -bottom-32 left-1/3 w-96 h-96 bg-emerald-500/10 rounded-full mix-blend-screen filter blur-3xl opacity-30 animate-blob animation-delay-4000"></div>
    </div>

    <!-- Mobile Header -->
    <div v-if="$route.name !== 'Login'" class="md:hidden flex items-center justify-between p-4 glass sticky top-0 z-50 border-b-0">
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-white/10 to-transparent flex items-center justify-center border border-white/5">
          <span class="iconify text-white" data-icon="lucide:trending-up" data-width="16"></span>
        </div>
      </div>
      <button class="text-white">
        <span class="iconify" data-icon="lucide:menu" data-width="20"></span>
      </button>
    </div>

    <!-- Sidebar -->
    <Sidebar v-if="$route.name !== 'Login'" />

    <!-- Main Content -->
    <main class="flex-1 flex flex-col h-full overflow-hidden relative z-10">
      <Header v-if="$route.name !== 'Login'" />
      <div :class="['flex-1 overflow-y-auto', $route.name !== 'Login' ? 'px-8 pt-6 pb-8 space-y-6' : '']">
        <RouterView />
      </div>
    </main>
  </div>
</template>
