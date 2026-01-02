<script setup>
// Nuxt auto-imports

// SEO
useSeoMeta({
  title: 'Swap - CactusFund',
  description: 'Wrap ETH to WETH or unwrap WETH to ETH instantly on Ethereum. No protocol fees, only gas costs.',
  ogTitle: 'ETH/WETH Swap - CactusFund',
  ogDescription: 'Wrap or unwrap ETH instantly on Ethereum. No protocol fees.',
  ogUrl: 'https://cactusfund.app/swap',
})

useHead({
  link: [{ rel: 'canonical', href: 'https://cactusfund.app/swap' }]
})

// AppKit (wallet connection) - using composable
const { accountData, openModal } = useAppKit()

// WETH operations - using composable
const { getEthBalance, getWethBalance, wrapEth, unwrapWeth, getExplorerTxUrl } = useWeth()

// Wallet state from AppKit
const isConnected = computed(() => Boolean(accountData.value?.isConnected))
const userAddress = computed(() => {
  if (!accountData.value?.address) return ''
  let addr = String(accountData.value.address)
  // Remove CAIP prefix if present (e.g., "eip155:1:")
  if (addr.includes(':')) {
    addr = addr.split(':').pop()
  }
  return addr
})

// Swap state
const direction = ref('wrap') // 'wrap' or 'unwrap'
const amount = ref('')
const ethBalance = ref('0')
const wethBalance = ref('0')
const isLoading = ref(false)
const isLoadingBalances = ref(false)
const txStatus = ref(null) // { success, hash, message, pending }

// Computed for display
const fromToken = computed(() => direction.value === 'wrap' ? 'ETH' : 'WETH')
const toToken = computed(() => direction.value === 'wrap' ? 'WETH' : 'ETH')
const fromBalance = computed(() => direction.value === 'wrap' ? ethBalance.value : wethBalance.value)
const toBalance = computed(() => direction.value === 'wrap' ? wethBalance.value : ethBalance.value)

// Fetch balances using useWeth composable
const fetchBalances = async () => {
  if (!isConnected.value || !userAddress.value) return

  isLoadingBalances.value = true
  try {
    const [eth, weth] = await Promise.all([
      getEthBalance(userAddress.value),
      getWethBalance(userAddress.value)
    ])
    ethBalance.value = eth
    wethBalance.value = weth
  } catch (error) {
    console.error('Failed to fetch balances:', error)
  } finally {
    isLoadingBalances.value = false
  }
}

// Watch for wallet connection changes
watch([isConnected, userAddress], () => {
  if (isConnected.value && userAddress.value) {
    fetchBalances()
  } else {
    // Reset balances when disconnected
    ethBalance.value = '0'
    wethBalance.value = '0'
  }
}, { immediate: true })

// Switch direction
const switchDirection = () => {
  direction.value = direction.value === 'wrap' ? 'unwrap' : 'wrap'
  amount.value = ''
  txStatus.value = null
}

// Set max amount
const setMax = () => {
  if (direction.value === 'wrap') {
    // Leave some ETH for gas
    const max = Math.max(0, parseFloat(ethBalance.value) - 0.01)
    amount.value = max > 0 ? max.toFixed(6) : '0'
  } else {
    amount.value = wethBalance.value
  }
}

// Execute swap using useWeth composable
const executeSwapAction = async () => {
  if (!isConnected.value || !amount.value || parseFloat(amount.value) <= 0) return

  isLoading.value = true
  txStatus.value = { pending: true, message: 'Waiting for wallet confirmation...' }

  try {
    // Call wrap or unwrap based on direction
    const tx = direction.value === 'wrap' 
      ? await wrapEth(amount.value, userAddress.value)
      : await unwrapWeth(amount.value, userAddress.value)

    txStatus.value = { pending: true, hash: tx.hash, message: 'Transaction pending...' }

    await tx.wait()

    txStatus.value = {
      success: true,
      hash: tx.hash,
      message: direction.value === 'wrap' ? 'ETH wrapped successfully!' : 'WETH unwrapped successfully!'
    }

    // Refresh balances
    await fetchBalances()
    amount.value = ''

  } catch (error) {
    console.error('Swap failed:', error)
    txStatus.value = {
      success: false,
      message: error.shortMessage || error.reason || error.message || 'Transaction failed'
    }
  } finally {
    isLoading.value = false
  }
}

// Format balance for display
const formatBalance = (balance) => {
  const num = parseFloat(balance)
  if (isNaN(num)) return '0'
  return num.toFixed(6)
}

// Handle connect wallet click
const handleConnectWallet = () => {
  openModal()
}

onMounted(() => {
  if (isConnected.value) {
    fetchBalances()
  }
})
</script>

<template>
  <div class="w-full max-w-md lg:max-w-lg mx-auto px-3 sm:px-0">
    <!-- Header -->
    <div class="mb-4 sm:mb-6 lg:mb-8 text-center">
      <h1 class="text-xl sm:text-2xl font-bold text-white mb-1 sm:mb-2">Swap</h1>
      <p class="text-xs sm:text-sm text-neutral-500">Wrap or unwrap your ETH instantly</p>
    </div>

    <!-- Swap Card -->
    <div class="glass-card rounded-2xl sm:rounded-3xl p-4 sm:p-5 lg:p-6 border border-white/10">

      <!-- From Token -->
      <div class="mb-3 sm:mb-4">
        <div class="flex items-center justify-between mb-1.5 sm:mb-2">
          <span class="text-xs text-neutral-500">From</span>
          <span class="text-xs text-neutral-500">
            Balance: {{ formatBalance(fromBalance) }} {{ fromToken }}
            <button
              @click="setMax"
              class="ml-1 text-emerald-400 hover:text-emerald-300 transition-colors"
            >MAX</button>
          </span>
        </div>
        <div class="bg-white/5 border border-white/10 rounded-xl sm:rounded-2xl p-3 sm:p-4 flex items-center gap-3 sm:gap-4">
          <!-- Token Icon - ETH or WETH -->
          <div class="w-8 h-8 sm:w-10 sm:h-10 rounded-full overflow-hidden bg-neutral-800 flex items-center justify-center flex-shrink-0">
            <img
              v-if="fromToken === 'ETH'"
              src="https://app.paradex.trade/static/market/ETH.svg"
              alt="ETH"
              class="w-full h-full object-cover"
            />
            <img
              v-else
              src="https://etherscan.io/token/images/weth_28.png?v=2"
              alt="WETH"
              class="w-full h-full object-cover"
            />
          </div>
          <!-- Amount Input -->
          <input
            v-model="amount"
            type="number"
            step="0.0001"
            min="0"
            placeholder="0.0"
            class="flex-1 bg-transparent text-xl sm:text-2xl font-mono text-white placeholder-neutral-600 focus:outline-none min-w-0"
          />
          <!-- Token Symbol -->
          <span class="text-base sm:text-lg font-semibold text-white">{{ fromToken }}</span>
        </div>
      </div>

      <!-- Switch Button -->
      <div class="flex justify-center -my-1.5 sm:-my-2 relative z-10">
        <button
          @click="switchDirection"
          class="w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-[#0f0f13] border border-white/10 flex items-center justify-center text-neutral-400 hover:text-white hover:border-emerald-500/50 transition-all hover:rotate-180 duration-300"
        >
          <svg class="w-4 h-4 sm:w-5 sm:h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"/>
          </svg>
        </button>
      </div>

      <!-- To Token -->
      <div class="mt-3 sm:mt-4 mb-4 sm:mb-5 lg:mb-6">
        <div class="flex items-center justify-between mb-1.5 sm:mb-2">
          <span class="text-xs text-neutral-500">To</span>
          <span class="text-xs text-neutral-500">
            Balance: {{ formatBalance(toBalance) }} {{ toToken }}
          </span>
        </div>
        <div class="bg-white/5 border border-white/10 rounded-xl sm:rounded-2xl p-3 sm:p-4 flex items-center gap-3 sm:gap-4">
          <!-- Token Icon - ETH or WETH -->
          <div class="w-8 h-8 sm:w-10 sm:h-10 rounded-full overflow-hidden bg-neutral-800 flex items-center justify-center flex-shrink-0">
            <img
              v-if="toToken === 'ETH'"
              src="https://app.paradex.trade/static/market/ETH.svg"
              alt="ETH"
              class="w-full h-full object-cover"
            />
            <img
              v-else
              src="https://etherscan.io/token/images/weth_28.png?v=2"
              alt="WETH"
              class="w-full h-full object-cover"
            />
          </div>
          <!-- Amount Display -->
          <span class="flex-1 text-xl sm:text-2xl font-mono text-white">
            {{ amount || '0.0' }}
          </span>
          <!-- Token Symbol -->
          <span class="text-base sm:text-lg font-semibold text-white">{{ toToken }}</span>
        </div>
      </div>

      <!-- Info Box -->
      <div class="bg-white/5 border border-white/5 rounded-lg sm:rounded-xl p-2.5 sm:p-3 mb-4 sm:mb-5 lg:mb-6">
        <div class="flex items-center justify-between text-xs">
          <span class="text-neutral-500">Rate</span>
          <span class="text-white font-mono">1 {{ fromToken }} = 1 {{ toToken }}</span>
        </div>
        <div class="flex items-center justify-between text-xs mt-1.5 sm:mt-2">
          <span class="text-neutral-500">Network</span>
          <span class="text-white">Ethereum</span>
        </div>
        <div class="flex items-center justify-between text-xs mt-1.5 sm:mt-2">
          <span class="text-neutral-500">Fee</span>
          <span class="text-emerald-400">Gas only (no protocol fee)</span>
        </div>
      </div>

      <!-- Action Button -->
      <button
        @click="executeSwapAction"
        :disabled="!isConnected || !amount || parseFloat(amount) <= 0 || isLoading"
        class="w-full py-3 sm:py-4 bg-gradient-to-r from-emerald-600 to-green-600 text-white text-sm sm:text-base font-bold rounded-xl sm:rounded-2xl hover:shadow-lg hover:shadow-emerald-500/20 hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none transition-all flex items-center justify-center gap-2"
      >
        <svg v-if="isLoading" class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <span v-else-if="!isConnected">Connect Wallet</span>
        <span v-else-if="!amount || parseFloat(amount) <= 0">Enter Amount</span>
        <span v-else>{{ direction === 'wrap' ? 'Wrap ETH' : 'Unwrap WETH' }}</span>
      </button>

      <!-- Transaction Status -->
      <div v-if="txStatus" class="mt-4">
        <!-- Pending -->
        <div v-if="txStatus.pending" class="p-3 bg-yellow-500/10 border border-yellow-500/20 rounded-xl">
          <div class="flex items-center gap-2 text-sm text-yellow-400">
            <svg class="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {{ txStatus.message }}
          </div>
          <a
            v-if="txStatus.hash"
            :href="getExplorerTxUrl(txStatus.hash)"
            target="_blank"
            class="text-xs text-yellow-500 hover:underline mt-1 block"
          >
            View on Etherscan →
          </a>
        </div>

        <!-- Success -->
        <div v-else-if="txStatus.success" class="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-xl">
          <div class="flex items-center gap-2 text-sm text-emerald-400">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            {{ txStatus.message }}
          </div>
          <a
            v-if="txStatus.hash"
            :href="getExplorerTxUrl(txStatus.hash)"
            target="_blank"
            class="text-xs text-emerald-500 hover:underline mt-1 block"
          >
            View on Etherscan →
          </a>
        </div>

        <!-- Error -->
        <div v-else-if="txStatus.success === false" class="p-3 bg-red-500/10 border border-red-500/20 rounded-xl">
          <div class="flex items-center gap-2 text-sm text-red-400">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
            {{ txStatus.message }}
          </div>
        </div>
      </div>
    </div>

    <!-- Info Footer -->
    <div class="mt-4 sm:mt-6 text-center">
      <p class="text-[10px] sm:text-xs text-neutral-600 px-2">
        WETH (Wrapped Ether) is an ERC-20 version of ETH used in DeFi protocols.
        <br/>
        Wrapping and unwrapping is always 1:1 with no fees beyond gas.
      </p>
    </div>
  </div>
</template>
