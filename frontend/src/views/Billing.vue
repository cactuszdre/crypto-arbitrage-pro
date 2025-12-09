<script setup>
import { useAuth } from '../stores/auth'
import { computed } from 'vue'

const { state: authState } = useAuth()
const isPro = computed(() => authState.user?.plan === 'PRO' || authState.user?.role === 'ADMIN')
</script>

<template>
  <div class="max-w-2xl mx-auto">
    <!-- Header -->
    <div class="text-center mb-10">
      <h1 class="text-3xl font-bold text-white mb-3">Pro Plan</h1>
      <p class="text-neutral-400">Unlock advanced features for serious traders</p>
    </div>

    <!-- Status Card -->
    <div class="glass-card rounded-3xl p-8 border border-amber-500/20 relative overflow-hidden">
      <!-- Background Glow -->
      <div class="absolute top-0 right-0 w-64 h-64 bg-amber-500/10 rounded-full blur-3xl -z-10"></div>
      <div class="absolute bottom-0 left-0 w-48 h-48 bg-orange-500/10 rounded-full blur-3xl -z-10"></div>

      <!-- Coming Soon Badge -->
      <div class="flex justify-center mb-6">
        <span class="px-4 py-1.5 text-sm font-bold uppercase tracking-wider bg-gradient-to-r from-amber-500 to-orange-500 text-white rounded-full">
          Coming Soon
        </span>
      </div>

      <!-- Main Message -->
      <div class="text-center mb-8">
        <div class="w-20 h-20 mx-auto mb-5 bg-gradient-to-br from-amber-500/20 to-orange-500/20 rounded-2xl flex items-center justify-center ring-2 ring-amber-500/30">
          <svg class="w-10 h-10 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
          </svg>
        </div>
        <h2 class="text-xl font-bold text-white mb-3">Pro Plan Not Available Yet</h2>
        <p class="text-neutral-400 leading-relaxed max-w-md mx-auto">
          We're working hard to bring you the Pro plan. Stay tuned for the official launch!
        </p>
      </div>

      <!-- Features Preview -->
      <div class="bg-white/5 rounded-2xl p-6 mb-8">
        <h3 class="text-sm font-bold text-white uppercase tracking-wider mb-4 flex items-center gap-2">
          <svg class="w-4 h-4 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
          </svg>
          Included Features
        </h3>
        <ul class="space-y-3">
          <li class="flex items-center gap-3 text-sm text-neutral-300">
            <div class="w-5 h-5 rounded-full bg-emerald-500/20 flex items-center justify-center flex-shrink-0">
              <svg class="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
              </svg>
            </div>
            Automatic Arbitrage Execution (Long + Short)
          </li>
          <li class="flex items-center gap-3 text-sm text-neutral-300">
            <div class="w-5 h-5 rounded-full bg-emerald-500/20 flex items-center justify-center flex-shrink-0">
              <svg class="w-3 h-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
              </svg>
            </div>
            Multi-DEX Parallel Execution
          </li>

          <li class="flex items-center gap-3 text-sm text-neutral-300">
            <div class="w-5 h-5 rounded-full bg-amber-500/20 flex items-center justify-center flex-shrink-0">
              <svg class="w-3 h-3 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
              </svg>
            </div>
            More features coming soon...
          </li>
        </ul>
      </div>

      <!-- CTA / Notify -->
      <div class="text-center">
        <button 
          disabled
          class="w-full py-4 bg-gradient-to-r from-amber-500/50 to-orange-500/50 text-white/70 text-sm font-bold rounded-xl cursor-not-allowed flex items-center justify-center gap-2"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
          </svg>
          Notify Me When Available
        </button>
        <p class="text-[11px] text-neutral-600 mt-3">
          We'll send you an update when the Pro plan launches.
        </p>
      </div>
    </div>

    <!-- Current Plan Info -->
    <div class="mt-6 text-center">
      <p class="text-sm text-neutral-500">
        Current Plan: 
        <span :class="isPro ? 'text-amber-400 font-semibold' : 'text-neutral-400'">
          {{ isPro ? 'Pro' : 'Free' }}
        </span>
      </p>
    </div>
  </div>
</template>
