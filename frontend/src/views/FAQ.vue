<script setup>
import { ref, nextTick } from 'vue'
import { chatApi } from '../services/api'

// Track which sections are open
const openSections = ref({})

const toggleSection = (id) => {
  openSections.value[id] = !openSections.value[id]
}

const isOpen = (id) => openSections.value[id]

// Chat state
const chatOpen = ref(false)
const chatInput = ref('')
const chatMessages = ref([])
const chatLoading = ref(false)
const chatContainer = ref(null)
const showChatHint = ref(true)

const toggleChat = () => {
  chatOpen.value = !chatOpen.value
  showChatHint.value = false // Hide hint when chat is opened
  if (chatOpen.value && chatMessages.value.length === 0) {
    // Add welcome message
    chatMessages.value.push({
      role: 'assistant',
      content: "Hi! I'm your crypto assistant. Ask me anything about funding rates, arbitrage strategies, or crypto trading basics."
    })
  }
}

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return

  const userMessage = chatInput.value.trim()
  chatInput.value = ''

  // Add user message
  chatMessages.value.push({ role: 'user', content: userMessage })
  
  // Scroll to bottom
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }

  chatLoading.value = true

  try {
    const response = await chatApi.sendMessage(
      chatMessages.value.filter(m => m.role !== 'assistant' || chatMessages.value.indexOf(m) > 0)
        .slice(-10) // Keep last 10 messages for context
    )
    
    chatMessages.value.push({
      role: 'assistant',
      content: response.data.response || response.data.error || 'No response received.'
    })
  } catch (error) {
    chatMessages.value.push({
      role: 'assistant',
      content: 'Sorry, I encountered an error. Please try again.'
    })
  } finally {
    chatLoading.value = false
    await nextTick()
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  }
}

// FAQ Data
const faqCategories = [
  {
    id: 'basics',
    title: 'The Basics',
    icon: 'lucide:book-open',
    questions: [
      {
        id: 'what-is-crypto',
        question: "What is crypto trading?",
        answer: `Crypto trading means buying and selling digital currencies like Bitcoin or Ethereum to make profit.

With CactusFund, you don't need to predict if prices go up or down - we use a smarter strategy called arbitrage.`
      },
      {
        id: 'what-is-wallet',
        question: "What's a crypto wallet?",
        answer: `A crypto wallet is your digital bank account for crypto.

It has a public address (safe to share) and a private key (never share this one).

Popular options include MetaMask, Rainbow, or Trust Wallet. Just click "Connect Wallet" to get started.`
      },
      {
        id: 'what-is-dex',
        question: "What's a DEX?",
        answer: `DEX stands for Decentralized Exchange.

Unlike Coinbase or Binance, DEXs run on blockchain: you keep control of your funds, no sign-up required, and you can trade 24/7.

We use Hyperliquid, Paradex, and Lighter.`
      }
    ]
  },
  {
    id: 'funding',
    title: 'Funding Rates',
    icon: 'lucide:percent',
    questions: [
      {
        id: 'what-is-funding',
        question: "What's a funding rate?",
        answer: `Funding rates are payments between traders that happen every 8 hours.

If too many bet UP, they pay those betting DOWN (and vice versa).

Positive rate = longs pay shorts.
Negative rate = shorts pay longs.`
      },
      {
        id: 'why-rates-differ',
        question: "Why are rates different on each exchange?",
        answer: `Each exchange has different traders with different opinions.

For example: Paradex might be -0.05% while Hyperliquid is +0.03%.

This difference is exactly the opportunity we exploit!`
      }
    ]
  },
  {
    id: 'arbitrage',
    title: 'The Strategy',
    icon: 'lucide:zap',
    questions: [
      {
        id: 'what-is-arb',
        question: "How does funding rate arbitrage work?",
        answer: `Open a LONG where you GET paid, and a SHORT where you also GET paid.

Same size, opposite directions = price movements cancel out.

But you still collect funding from BOTH sides.

Example: $10k each side, 0.05% + 0.03% = $8 every 8 hours.`
      },
      {
        id: 'how-much-profit',
        question: "How much can I make?",
        answer: `Conservative: $10k position at 0.1% daily = $10/day or $300/month.

During high volatility: 0.3-1% daily is possible.

Returns vary with market conditions and position size.`
      },
      {
        id: 'what-risks',
        question: "What are the risks?",
        answer: `Main risks include:
- Execution risk (timing both trades)
- Funding rate changes
- Liquidation if one side moves too fast (use max 5x leverage)
- Smart contract bugs (rare on major DEXs)
- Exchange issues

Start small and learn the mechanics first.`
      }
    ]
  },
  {
    id: 'getting-started',
    title: 'Getting Started',
    icon: 'lucide:rocket',
    questions: [
      {
        id: 'what-need',
        question: "What do I need?",
        answer: `You'll need:
- A crypto wallet (MetaMask recommended)
- Some ETH or USDC ($500+ to start)
- $10-20 for gas fees

Steps: Install MetaMask, create wallet, save seed phrase offline, buy ETH, connect to CactusFund, and start small.`
      },
      {
        id: 'how-use-app',
        question: "How do I use CactusFund?",
        answer: `Dashboard shows current funding rates across all DEXs.

Opportunities page shows the best trades sorted by daily return.

Click an opportunity to see the Long/Short pair and execute both trades.

Look for daily returns above 0.1% for good opportunities.`
      },
      {
        id: 'common-mistakes',
        question: "What mistakes should I avoid?",
        answer: `Common mistakes:
- Going all-in (start with 10-20% of your capital)
- Ignoring trading and gas fees
- Using high leverage (5x max recommended)
- Not monitoring your positions
- FOMO trading every opportunity
- Forgetting about taxes
- Sharing your seed phrase (never do this)`
      }
    ]
  }
]
</script>

<template>
  <div class="max-w-3xl mx-auto pb-12">
    <!-- Hero Section -->
    <div class="text-center mb-8">
      <h1 class="text-2xl sm:text-3xl font-bold text-white mb-2">
        Learn Funding Rate
        <span class="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-green-500">Arbitrage</span>
      </h1>
      <p class="text-neutral-400 text-sm max-w-xl mx-auto">
        Everything you need to know about making money from funding rates
      </p>
    </div>

    <!-- Quick Stats -->
    <div class="grid grid-cols-4 gap-2 sm:gap-3 mb-8">
      <div class="glass-card p-3 rounded-xl text-center">
        <span class="iconify text-emerald-400 mx-auto mb-1" data-icon="lucide:trending-up" data-width="20"></span>
        <div class="text-sm sm:text-base font-bold text-white">0.1-1%</div>
        <div class="text-[9px] sm:text-[10px] text-neutral-500">Daily</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <span class="iconify text-blue-400 mx-auto mb-1" data-icon="lucide:scale" data-width="20"></span>
        <div class="text-sm sm:text-base font-bold text-white">Neutral</div>
        <div class="text-[9px] sm:text-[10px] text-neutral-500">Risk</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <span class="iconify text-purple-400 mx-auto mb-1" data-icon="lucide:clock" data-width="20"></span>
        <div class="text-sm sm:text-base font-bold text-white">24/7</div>
        <div class="text-[9px] sm:text-[10px] text-neutral-500">Earning</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <span class="iconify text-orange-400 mx-auto mb-1" data-icon="lucide:shield" data-width="20"></span>
        <div class="text-sm sm:text-base font-bold text-white">Low</div>
        <div class="text-[9px] sm:text-[10px] text-neutral-500">Risk</div>
      </div>
    </div>

    <!-- FAQ Categories -->
    <div class="space-y-4">
      <div 
        v-for="category in faqCategories" 
        :key="category.id"
        class="glass-card rounded-2xl overflow-hidden"
      >
        <!-- Category Header -->
        <div class="px-4 py-3 border-b border-white/5 flex items-center gap-3">
          <span class="iconify text-emerald-400" :data-icon="category.icon" data-width="18"></span>
          <h2 class="text-base font-semibold text-white">{{ category.title }}</h2>
        </div>

        <!-- Questions -->
        <div class="divide-y divide-white/5">
          <div 
            v-for="q in category.questions" 
            :key="q.id"
            class="group"
          >
            <!-- Question Button -->
            <button
              @click="toggleSection(q.id)"
              class="w-full px-4 py-3 flex items-center gap-3 text-left hover:bg-white/5 transition-colors"
            >
              <!-- Expand Icon -->
              <div 
                :class="[
                  'w-5 h-5 rounded flex items-center justify-center shrink-0 transition-all duration-200',
                  isOpen(q.id) 
                    ? 'bg-emerald-500 rotate-45' 
                    : 'bg-white/10'
                ]"
              >
                <span class="iconify text-white" data-icon="lucide:plus" data-width="12"></span>
              </div>
              
              <!-- Question Text -->
              <span :class="['text-sm font-medium transition-colors flex-1', isOpen(q.id) ? 'text-emerald-400' : 'text-white']">
                {{ q.question }}
              </span>
              
              <!-- Arrow -->
              <span 
                :class="[
                  'iconify text-neutral-500 transition-transform duration-200',
                  isOpen(q.id) ? 'rotate-180' : ''
                ]" 
                data-icon="lucide:chevron-down" 
                data-width="14"
              ></span>
            </button>

            <!-- Answer -->
            <div 
              v-show="isOpen(q.id)"
              class="px-4 pb-4 pt-2"
            >
              <div class="ml-8 p-3 rounded-lg bg-white/[0.02] border border-white/5 text-sm text-neutral-300 leading-relaxed whitespace-pre-line">
                {{ q.answer }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- CTA Section -->
    <div class="mt-8 glass-card rounded-2xl p-5 flex flex-col sm:flex-row items-center justify-between gap-4">
      <div>
        <h3 class="text-base font-semibold text-white">Ready to start?</h3>
        <p class="text-neutral-400 text-sm">Check out today's best opportunities</p>
      </div>
      <router-link 
        to="/arbitrage" 
        class="flex items-center gap-2 px-5 py-2.5 rounded-lg bg-gradient-to-r from-emerald-500 to-green-600 text-white text-sm font-semibold hover:from-emerald-600 hover:to-green-700 transition-all"
      >
        <span>View Opportunities</span>
        <span class="iconify" data-icon="lucide:arrow-right" data-width="16"></span>
      </router-link>
    </div>

    <!-- AI Chat Button (Fixed) -->
    <div class="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-2">
      <!-- Chat Hint Tooltip -->
      <div 
        v-if="showChatHint && !chatOpen"
        class="glass-card px-4 py-2 rounded-xl border border-purple-500/30 shadow-lg animate-bounce-slow"
      >
        <div class="flex items-center gap-2">
          <span class="iconify text-purple-400" data-icon="lucide:sparkles" data-width="14"></span>
          <span class="text-sm text-white">Still need help? Ask our AI</span>
          <button @click.stop="showChatHint = false" class="text-neutral-500 hover:text-white ml-1">
            <span class="iconify" data-icon="lucide:x" data-width="12"></span>
          </button>
        </div>
      </div>
      
      <!-- Chat Button -->
      <button
        @click="toggleChat"
        :class="[
          'w-14 h-14 rounded-full flex items-center justify-center shadow-lg transition-all duration-300',
          chatOpen 
            ? 'bg-red-500 hover:bg-red-600 rotate-45' 
            : 'bg-gradient-to-r from-purple-500 to-indigo-600 hover:from-purple-600 hover:to-indigo-700'
        ]"
      >
        <span class="iconify text-white" :data-icon="chatOpen ? 'lucide:plus' : 'lucide:message-circle'" data-width="24"></span>
      </button>
    </div>

    <!-- AI Chat Panel -->
    <div 
      v-show="chatOpen"
      class="fixed bottom-24 right-6 w-80 sm:w-96 h-[450px] glass-card rounded-2xl shadow-2xl border border-white/10 flex flex-col overflow-hidden z-50"
    >
      <!-- Chat Header -->
      <div class="px-4 py-3 border-b border-white/10 flex items-center gap-3">
        <div class="w-8 h-8 rounded-full bg-gradient-to-r from-purple-500 to-indigo-600 flex items-center justify-center">
          <span class="iconify text-white" data-icon="lucide:bot" data-width="18"></span>
        </div>
        <div class="flex-1">
          <h3 class="text-sm font-semibold text-white">AI Assistant</h3>
          <p class="text-[10px] text-neutral-500">Powered by Mistral AI</p>
        </div>
        <button @click="toggleChat" class="text-neutral-500 hover:text-white transition-colors">
          <span class="iconify" data-icon="lucide:x" data-width="18"></span>
        </button>
      </div>

      <!-- Chat Messages -->
      <div 
        ref="chatContainer"
        class="flex-1 overflow-y-auto p-4 space-y-3"
      >
        <div 
          v-for="(msg, index) in chatMessages" 
          :key="index"
          :class="[
            'max-w-[85%] p-3 rounded-xl text-sm',
            msg.role === 'user' 
              ? 'ml-auto bg-emerald-500/20 border border-emerald-500/30 text-white' 
              : 'bg-white/5 border border-white/10 text-neutral-300'
          ]"
        >
          <p class="whitespace-pre-wrap">{{ msg.content }}</p>
        </div>

        <!-- Loading indicator -->
        <div v-if="chatLoading" class="flex items-center gap-2 text-neutral-500 text-sm">
          <div class="flex gap-1">
            <span class="w-2 h-2 rounded-full bg-purple-500 animate-bounce" style="animation-delay: 0ms"></span>
            <span class="w-2 h-2 rounded-full bg-purple-500 animate-bounce" style="animation-delay: 150ms"></span>
            <span class="w-2 h-2 rounded-full bg-purple-500 animate-bounce" style="animation-delay: 300ms"></span>
          </div>
          <span>Thinking...</span>
        </div>
      </div>

      <!-- Chat Input -->
      <div class="p-3 border-t border-white/10">
        <div class="flex gap-2">
          <input
            v-model="chatInput"
            @keyup.enter="sendMessage"
            type="text"
            placeholder="Ask about funding rates..."
            class="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-neutral-500 focus:outline-none focus:border-purple-500/50"
          />
          <button
            @click="sendMessage"
            :disabled="chatLoading || !chatInput.trim()"
            class="px-3 py-2 rounded-lg bg-gradient-to-r from-purple-500 to-indigo-600 text-white hover:from-purple-600 hover:to-indigo-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span class="iconify" data-icon="lucide:send" data-width="16"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes bounce-slow {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-5px);
  }
}

.animate-bounce-slow {
  animation: bounce-slow 2s ease-in-out infinite;
}
</style>
