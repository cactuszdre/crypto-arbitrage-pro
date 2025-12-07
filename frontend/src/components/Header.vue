<script setup>
import { ref, computed, watchEffect, unref } from 'vue'
import { useAppKit, useAppKitAccount } from '@reown/appkit/vue'

const searchQuery = ref('')
const { open } = useAppKit()

// State refs
const isConnected = ref(false)
const userAddress = ref('')

const account = useAppKitAccount()

// Robustly track state changes
watchEffect(() => {
  // Check if properties might be refs or direct values
  // In some versions, it's a reactive object, in others refs
  const connected = unref(account.isConnected)
  const addr = unref(account.address)
  
  console.log('Wallet Debug:', { connected, addr })
  
  isConnected.value = !!connected
  userAddress.value = addr || ''
})

const formattedAddress = computed(() => {
  if (!userAddress.value) return ''
  return `${userAddress.value.slice(0, 6)}...${userAddress.value.slice(-4)}`
})
</script>

<template>
  <header class="h-20 flex items-center justify-between px-8">
    <div>
      <h1 class="text-xl font-medium text-white tracking-tight">Good morning, Trader</h1>
      <p class="text-xs text-neutral-500 mt-0.5">Monitor DEX funding rates in real-time</p>
    </div>

    <div class="flex items-center gap-4">
      <!-- Search -->
      <div class="glass-card rounded-full px-4 py-2 flex items-center gap-3 transition-all hover:bg-white/5">
        <span class="iconify text-neutral-400" data-icon="lucide:search" data-width="14"></span>
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search assets..." 
          class="bg-transparent border-none text-xs text-white placeholder-neutral-500 focus:outline-none w-48"
        >
        <span class="text-[10px] text-neutral-600 border border-neutral-700 px-1.5 rounded">⌘K</span>
      </div>
      
      <!-- Notifications -->
      <button class="w-9 h-9 rounded-full glass-card flex items-center justify-center text-neutral-400 hover:text-white transition-colors relative">
        <span class="iconify" data-icon="lucide:bell" data-width="16"></span>
        <span class="absolute top-2.5 right-2.5 w-1.5 h-1.5 bg-indigo-500 rounded-full"></span>
      </button>

      <!-- Connect Wallet Custom Button -->
      <button 
        @click="open()" 
        class="glass-card rounded-full px-4 py-2 flex items-center gap-2 text-xs font-medium text-white hover:bg-white/5 transition-all border border-white/5 hover:border-white/10"
      >
        <span class="iconify" :data-icon="isConnected ? 'lucide:wallet' : 'lucide:plug'" data-width="14"></span>
        {{ isConnected ? formattedAddress : 'Connect Wallet' }}
      </button>
    </div>
  </header>
</template>
