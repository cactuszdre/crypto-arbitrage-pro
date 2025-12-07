<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppKit, useAppKitAccount } from '@reown/appkit/vue'
import { useAuth } from '../stores/auth'
import { userApi } from '../services/api'

const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const { open } = useAppKit()
const accountData = useAppKitAccount()
const { setUser, logout, state } = useAuth()

// Watch for wallet connection changes and sync with our auth store
watch(
  () => accountData.value,
  async (newAccount) => {
    console.log('🔄 Account changed:', newAccount)
    
    if (newAccount?.isConnected && newAccount?.address) {
      // Extract clean address (remove CAIP prefix like "eip155:1:")
      let cleanAddress = newAccount.address
      if (cleanAddress.includes(':')) {
        cleanAddress = cleanAddress.split(':').pop()
      }
      
      console.log('🔑 Clean wallet address:', cleanAddress)
      
      try {
        // Login or register user with wallet, then get their role from backend
        const response = await userApi.updateLastLogin(cleanAddress)
        setUser(response.data)
        console.log('✅ User synced with backend:', response.data)
        
        // If user is on admin page but not admin, redirect to home
        if (route.path === '/admin' && response.data.role !== 'ADMIN') {
          console.warn('🚫 Non-admin on admin page, redirecting...')
          router.push('/')
        }
      } catch (error) {
        console.error('Failed to sync user with backend:', error)
        // Set basic user data even if backend fails
        setUser({ walletAddress: cleanAddress, role: 'USER', plan: 'FREE' })
        
        // Redirect from admin page if not admin
        if (route.path === '/admin') {
          router.push('/')
        }
      }
    } else {
      // Wallet disconnected
      logout()
      console.log('👋 User logged out')
      
      // Redirect from admin page if disconnected
      if (route.path === '/admin') {
        router.push('/')
      }
    }
  },
  { immediate: true, deep: true }
)

const isConnected = computed(() => {
  if (!accountData || !accountData.value) return false
  return Boolean(accountData.value.isConnected)
})

const formattedAddress = computed(() => {
  if (!accountData || !accountData.value || !accountData.value.address) return ''
  const addr = String(accountData.value.address)
  return `${addr.slice(0, 6)}...${addr.slice(-4)}`
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
