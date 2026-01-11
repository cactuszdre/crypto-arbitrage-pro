<script setup>
const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const { setUser, logout, state } = useAuth()

// Real-time clock
const currentTime = ref(new Date())
let clockInterval = null

// OS Detection for keyboard shortcut
const isMac = ref(false)

// AppKit (wallet connection) - using composable
const { appKit, accountData, isInitializing, isInitialized, openModal, wagmiAdapter, hasEverConnected } = useAppKit()

// Lock to prevent duplicate login attempts
const isLoggingIn = ref(false)

const searchShortcut = computed(() => isMac.value ? '⌘K' : 'Ctrl+K')

// Search functionality - redirect to arbitrage page
const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/opportunites', query: { search: searchQuery.value.trim() } })
  } else {
    router.push('/opportunites')
  }
}

// Handle keyboard shortcut (Ctrl+K or Cmd+K)
const handleKeyboardShortcut = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    document.querySelector('.search-input')?.focus()
  }
}

const formatTimeUTC = computed(() => {
  const time = currentTime.value
  const hours = String(time.getUTCHours()).padStart(2, '0')
  const minutes = String(time.getUTCMinutes()).padStart(2, '0')
  const seconds = String(time.getUTCSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds} UTC`
})

const isConnected = computed(() => {
  // Check AppKit connection OR stored user session
  return Boolean(accountData.value?.isConnected) || Boolean(state.user?.walletAddress)
})

const formattedAddress = computed(() => {
  // Prefer AppKit address, fallback to stored user
  const addr = accountData.value?.address || state.user?.walletAddress
  if (!addr) return ''
  // Remove CAIP prefix if present
  const cleanAddr = addr.includes(':') ? addr.split(':').pop() : addr
  return `${cleanAddr.slice(0, 6)}...${cleanAddr.slice(-4)}`
})

// AppKit loading state (from composable)
const isAppKitLoading = isInitializing

// Open wallet modal using composable
const openWallet = () => {
  openModal()
}

// Watch for wallet connection changes (client-side only)
if (import.meta.client) {
  watch(
    () => accountData.value?.address,
    async (newAddress, oldAddress) => {
      if (newAddress) {
        // Extract clean address (remove CAIP prefix like "eip155:1:")
        let cleanAddress = newAddress
        if (cleanAddress.includes(':')) {
          cleanAddress = cleanAddress.split(':').pop()
        }
        
        // Check if we already have this user in localStorage (page refresh scenario)
        const storedUser = localStorage.getItem('user')
        if (storedUser) {
          try {
            const parsed = JSON.parse(storedUser)
            if (parsed.walletAddress?.toLowerCase() === cleanAddress.toLowerCase()) {
              // User is already authenticated from previous session, just restore state
              if (!state.user?.walletAddress) {
                setUser(parsed)
              }
              return
            } else {
              // WALLET SWITCH DETECTED: Different address than stored user
              // Immediately logout to revoke access (especially admin rights)
              console.log('[Header] Wallet switch detected! Logging out previous user:', parsed.walletAddress, '-> New:', cleanAddress)
              logout()
              localStorage.removeItem('user')
              
              // Redirect from admin page immediately
              if (route.path === '/admin') {
                router.push('/')
              }
            }
          } catch (e) {
            console.error('Failed to parse stored user:', e)
          }
        }
        
        // Also check current state (in case localStorage was already cleared)
        if (state.user?.walletAddress && state.user.walletAddress.toLowerCase() !== cleanAddress.toLowerCase()) {
          console.log('[Header] Wallet switch detected (state)! Logging out:', state.user.walletAddress, '-> New:', cleanAddress)
          logout()
          if (route.path === '/admin') {
            router.push('/')
          }
        }
        
        // Prevent duplicate login if currently logging in
        if (isLoggingIn.value) return
        
        isLoggingIn.value = true
        
        try {
          // 1. Get Nonce from backend
          const nonceRes = await $fetch(`/api/auth/nonce/${cleanAddress}`)
          const nonce = nonceRes.nonce

          if (!nonce) throw new Error('Failed to get nonce')
          
          // 2. Sign Message
          if (!wagmiAdapter.value) throw new Error('Wagmi Adapter not ready')
          const { signMessage } = await import('@wagmi/core')
          
          const signature = await signMessage(wagmiAdapter.value.wagmiConfig, { 
              message: nonce 
          })

          // 3. Login with signature
          const response = await $fetch('/api/auth/login', {
            method: 'POST',
            body: {
                walletAddress: cleanAddress,
                signature: signature
            }
          })
          
          setUser(response)
          
          // If user is on admin page but not admin, redirect to home
          if (route.path === '/admin' && response.role !== 'ADMIN') {
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
        } finally {
          isLoggingIn.value = false
        }
      } else {
        // Wallet disconnected via AppKit modal
        // IMPORTANT: Only logout if:
        // 1. AppKit has finished initializing (isInitialized)
        // 2. User had actually connected before (hasEverConnected)
        // 3. AppKit explicitly says disconnected
        // This prevents false logout on page refresh before AppKit restores session
        if (isInitialized.value && hasEverConnected.value && accountData.value?.isConnected === false) {
            console.log('[Header] Wallet disconnected (after being connected), clearing session')
            logout()
            localStorage.removeItem('user')
            
            // Redirect from admin page if disconnected
            if (route.path === '/admin') {
              router.push('/')
            }
        }
      }
    },
    { immediate: true }
  )
  
  // Also watch isConnected explicitly for disconnect events
  watch(
    () => accountData.value?.isConnected,
    (isConnected, wasConnected) => {
      // Only trigger on explicit disconnect (was true, now false)
      // Also check hasEverConnected to avoid false triggers on initialization
      if (wasConnected === true && isConnected === false && hasEverConnected.value) {
        console.log('[Header] AppKit isConnected changed to false, logging out')
        logout()
        localStorage.removeItem('user')
        
        if (route.path === '/admin') {
          router.push('/')
        }
      }
    }
  )
}

onMounted(() => {
  // Restore user session from localStorage immediately on mount
  const storedUser = localStorage.getItem('user')
  if (storedUser && !state.user?.walletAddress) {
    try {
      const parsed = JSON.parse(storedUser)
      if (parsed.walletAddress) {
        setUser(parsed)
      }
    } catch (e) {
      console.error('Failed to restore user session:', e)
    }
  }

  // OS detection
  isMac.value = navigator.platform.toUpperCase().indexOf('MAC') >= 0 || 
                navigator.userAgent.toUpperCase().indexOf('MAC') >= 0
  
  // Keyboard shortcut
  window.addEventListener('keydown', handleKeyboardShortcut)
  
  // Start clock
  clockInterval = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
  
  // Initialize AppKit (done via plugin, get reference here)
  if (typeof window !== 'undefined' && window.__appKit) {
    appKit.value = window.__appKit
    // Subscribe to account changes
    if (appKit.value.subscribeAccount) {
      appKit.value.subscribeAccount((account) => {
        accountData.value = account
      })
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
  if (clockInterval) clearInterval(clockInterval)
})
</script>

<template>
  <!-- Header -->
  <header class="header-main">
    <div class="header-left">
      <!-- Logo CactusFund (visible on mobile only) -->
      <NuxtLink to="/" class="mobile-logo">
        <img src="~/assets/logo.png" alt="CactusFund" class="mobile-logo-img" />
        <span class="mobile-logo-text">CactusFund</span>
      </NuxtLink>

      <!-- System Status Badge (hidden on mobile) -->
      <div class="status-badge">
        <div class="status-dot">
          <span class="status-dot-inner"></span>
        </div>
        <span class="status-text">System Operational</span>
      </div>
      
      <!-- Separator (hidden on mobile) -->
      <div class="separator"></div>
      
      <!-- Clock (hidden on mobile) -->
      <div class="clock-section">
        <ClientOnly>
          <span class="iconify clock-icon" data-icon="lucide:clock" data-width="12"></span>
          <span class="clock-time">{{ formatTimeUTC }}</span>
          <template #fallback>
            <span class="clock-time">--:--:-- UTC</span>
          </template>
        </ClientOnly>
      </div>
    </div>

    <div class="header-right">
      <!-- Search - Compact on mobile -->
      <div class="search-box">
        <ClientOnly>
          <span class="iconify search-icon" data-icon="lucide:search" data-width="14" @click="handleSearch" style="cursor: pointer;"></span>
        </ClientOnly>
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search assets..." 
          class="search-input"
          @keyup.enter="handleSearch"
        >
        <span class="search-shortcut">{{ searchShortcut }}</span>
      </div>

      <!-- Connect Wallet Button -->
      <button 
        @click="openWallet" 
        class="wallet-button"
      >
        <!-- Icon only on mobile -->
        <span class="wallet-icon">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/>
          </svg>
          <!-- Connected indicator -->
          <span v-if="isConnected" class="wallet-connected-dot"></span>
        </span>
        <!-- Text on larger screens -->
        <span class="wallet-text">{{ isConnected ? formattedAddress : 'Connect Wallet' }}</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
/* ===== HEADER ===== */
.header-main {
  height: 56px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.75rem;
  position: sticky;
  top: 0;
  z-index: 30;
}

@media (min-width: 768px) {
  .header-main {
    height: 72px;
    padding: 0 1.5rem;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .header-left {
    gap: 1rem;
  }
}

/* Mobile Logo (visible on mobile only) */
.mobile-logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .mobile-logo {
    display: none;
  }
}

.mobile-logo-img {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.2);
}

.mobile-logo-text {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: -0.02em;
}

/* Status Badge (hidden on mobile) */
.status-badge {
  display: none;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background: rgba(24, 24, 27, 0.5);
  border: 1px solid #27272a;
}

@media (min-width: 768px) {
  .status-badge {
    display: flex;
  }
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse-glow 2s ease-in-out infinite;
  flex-shrink: 0;
}

@media (min-width: 768px) {
  .status-dot {
    width: 8px;
    height: 8px;
  }
}

.status-dot-inner {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #10b981;
}

@keyframes pulse-glow {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(16, 185, 129, 0);
  }
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: #34d399;
}

/* Separator */
.separator {
  width: 1px;
  height: 16px;
  background: #27272a;
  display: none;
}

@media (min-width: 768px) {
  .separator {
    display: block;
  }
}

/* Clock */
.clock-section {
  display: none;
  align-items: center;
  gap: 0.5rem;
  color: #71717a;
}

@media (min-width: 768px) {
  .clock-section {
    display: flex;
  }
}

.clock-icon {
  opacity: 0.7;
}

.clock-time {
  font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
}

/* Right Section */
.header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (min-width: 768px) {
  .header-right {
    gap: 0.75rem;
  }
}

/* Search Box */
.search-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  height: 32px;
  background: #18181b;
  border: 1px solid #27272a;
  border-radius: 8px;
  transition: all 0.2s ease;
}

@media (min-width: 768px) {
  .search-box {
    padding: 0.375rem 0.75rem;
    height: 36px;
    border-radius: 10px;
  }
}

.search-box:hover,
.search-box:focus-within {
  border-color: #3f3f46;
}

.search-icon {
  color: #71717a;
  flex-shrink: 0;
}

.search-input {
  background: transparent;
  border: none;
  outline: none;
  font-size: 12px;
  color: #d4d4d8;
  width: 80px;
  min-width: 0;
}

@media (min-width: 640px) {
  .search-input {
    width: 100px;
  }
}

@media (min-width: 768px) {
  .search-input {
    width: 120px;
  }
}

.search-input::placeholder {
  color: #52525b;
}

.search-shortcut {
  font-size: 10px;
  color: #52525b;
  padding: 0.125rem 0.375rem;
  border: 1px solid #3f3f46;
  border-radius: 3px;
  background: rgba(39, 39, 42, 0.5);
  display: none;
}

@media (min-width: 640px) {
  .search-shortcut {
    display: block;
  }
}

/* Wallet Button */
.wallet-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: #ffffff;
  color: #000000;
  padding: 0.375rem;
  height: 32px;
  width: 32px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
  white-space: nowrap;
}

@media (min-width: 640px) {
  .wallet-button {
    padding: 0.375rem 0.75rem;
    width: auto;
  }
}

@media (min-width: 768px) {
  .wallet-button {
    padding: 0.375rem 1rem;
    height: 36px;
    border-radius: 10px;
    font-size: 12px;
  }
}

.wallet-button:hover {
  background: #e4e4e7;
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.15);
}

/* Wallet Icon (visible on mobile) */
.wallet-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (min-width: 640px) {
  .wallet-icon {
    display: none;
  }
}

.wallet-icon svg {
  width: 18px;
  height: 18px;
}

/* Connected indicator dot */
.wallet-connected-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  border: 2px solid #ffffff;
  animation: pulse-glow 2s ease-in-out infinite;
}

/* Wallet Text (hidden on mobile) */
.wallet-text {
  display: none;
}

@media (min-width: 640px) {
  .wallet-text {
    display: inline;
  }
}
</style>
