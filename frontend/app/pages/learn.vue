<script setup>
// Nuxt auto-imports: ref, nextTick, computed

// ===== ENHANCED SEO =====
useSeoMeta({
  title: 'Learn | CactusFund',
  description: 'Master funding rate arbitrage with our complete guide. Learn how to earn 0.1-1% daily returns with low-risk crypto strategies on top DEXs.',
  ogTitle: 'Learn | CactusFund',
  ogDescription: 'The ultimate guide to funding rate arbitrage. Discover how to earn passive income from crypto with delta-neutral strategies.',
  ogType: 'article',
  twitterCard: 'summary_large_image',
  robots: 'index, follow',
})

// Structured data for FAQ (SEO)
useHead({
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "mainEntity": [
          {
            "@type": "Question",
            "name": "What is funding rate arbitrage?",
            "acceptedAnswer": {
              "@type": "Answer",
              "text": "Funding rate arbitrage is a low-risk crypto trading strategy that profits from differences in funding rates between exchanges. By opening opposing positions (long and short) on different platforms, traders can earn the spread while remaining market-neutral."
            }
          },
          {
            "@type": "Question",
            "name": "How much can I earn with funding rate arbitrage?",
            "acceptedAnswer": {
              "@type": "Answer",
              "text": "Returns typically range from 0.1% to 1% daily depending on market conditions. With a $10,000 position at 0.1% daily, you could earn approximately $10/day or $300/month in passive income."
            }
          },
          {
            "@type": "Question",
            "name": "Is funding rate arbitrage safe?",
            "acceptedAnswer": {
              "@type": "Answer",
              "text": "Funding rate arbitrage is considered low-risk because positions are delta-neutral (price movements cancel out). However, risks include execution timing, funding rate changes, and potential liquidation with high leverage. Using max 5x leverage is recommended."
            }
          },
          {
            "@type": "Question",
            "name": "What is CactusFund?",
            "acceptedAnswer": {
              "@type": "Answer",
              "text": "CactusFund is a platform that aggregates funding rates from multiple decentralized exchanges and identifies the best arbitrage opportunities. It's non-custodial, meaning you keep full control of your funds."
            }
          }
        ]
      })
    }
  ]
})

// Track which sections are open
const openSections = ref({})

const toggleSection = (id) => {
  openSections.value[id] = !openSections.value[id]
}

const isOpen = (id) => openSections.value[id]

// Open a specific section and scroll to it
const openAndScrollTo = (questionId) => {
  openSections.value[questionId] = true
  
  nextTick(() => {
    const element = document.getElementById(`question-${questionId}`)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' })
      element.classList.add('highlight-flash')
      setTimeout(() => element.classList.remove('highlight-flash'), 1500)
    }
  })
}

// Parse answer text and convert [[term|questionId]] to clickable links
const parseAnswer = (answer) => {
  return answer.replace(/\[\[([^|\]]+)\|([^\]]+)\]\]/g, (match, text, id) => {
    return `<button class="faq-link" onclick="window.openFaqSection('${id}')">${text}</button>`
  })
}

// Expose function to window for onclick handlers
if (typeof window !== 'undefined') {
  window.openFaqSection = (id) => openAndScrollTo(id)
}

// Chat state
const chatOpen = ref(false)
const chatInput = ref('')
const chatMessages = ref([])
const chatLoading = ref(false)
const chatContainer = ref(null)
const showChatHint = ref(true)

const toggleChat = () => {
  chatOpen.value = !chatOpen.value
  showChatHint.value = false
  if (chatOpen.value && chatMessages.value.length === 0) {
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

  chatMessages.value.push({ role: 'user', content: userMessage })
  
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }

  chatLoading.value = true

  try {
    const response = await $fetch('/api/chat', {
      method: 'POST',
      body: { messages: chatMessages.value.slice(-10) }
    })
    
    chatMessages.value.push({
      role: 'assistant',
      content: response.response || 'No response received.'
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

// Active category for navigation
const activeCategory = ref('overview')

// How It Works Steps
const howItWorksSteps = [
  {
    step: 1,
    title: 'Scan Multiple DEXs',
    description: 'CactusFund monitors funding rates on top decentralized exchanges in real-time.',
    icon: 'lucide:scan',
    color: 'emerald'
  },
  {
    step: 2,
    title: 'Identify Spread',
    description: 'Find pairs where you receive funding on one exchange and pay less on another.',
    icon: 'lucide:target',
    color: 'blue'
  },
  {
    step: 3,
    title: 'Open Positions',
    description: 'Go LONG on the lower rate exchange and SHORT on the higher rate exchange.',
    icon: 'lucide:git-branch',
    color: 'purple'
  },
  {
    step: 4,
    title: 'Collect Profits',
    description: 'Your positions cancel price risk while you pocket the funding rate difference.',
    icon: 'lucide:wallet',
    color: 'orange'
  }
]

// Benefits
const benefits = [
  { icon: 'lucide:shield-check', title: 'Market Neutral', description: 'Profit regardless of price direction' },
  { icon: 'lucide:clock', title: '24/7 Earning', description: 'Funding paid every 8 hours automatically' },
  { icon: 'lucide:trending-up', title: '0.1-1% Daily', description: 'Consistent returns in all market conditions' },
  { icon: 'lucide:lock', title: 'Non-Custodial', description: 'Your keys, your crypto, always' },
  { icon: 'lucide:zap', title: 'Real-Time Data', description: 'Opportunities updated every 10 seconds' },
  { icon: 'lucide:brain', title: 'AI Assistant', description: 'Get help from our intelligent chatbot' }
]

// FAQ Data - Enhanced for SEO
const faqCategories = [
  {
    id: 'cactusfund',
    title: 'About CactusFund',
    icon: 'lucide:leaf',
    questions: [
      {
        id: 'what-is-cactusfund',
        question: "What is CactusFund?",
        answer: `CactusFund is your all-in-one platform for earning passive income from [[crypto|what-is-crypto]].

We find the best [[funding rate|what-is-funding]] opportunities across multiple exchanges and show you exactly how to profit from them.

No complex charts. No guessing if prices go up or down. Just steady, low-risk earnings.`
      },
      {
        id: 'how-cactusfund-works',
        question: "How does CactusFund work?",
        answer: `CactusFund scans multiple [[DEXs|what-is-dex]] (decentralized exchanges) every few seconds.

It finds pairs where the [[funding rates|what-is-funding]] are different:
- You open a LONG on Exchange A
- You open a SHORT on Exchange B (same size)

Sometimes you'll pay funding on one side, but receive MORE on the other side.

The difference is your profit! Example: you pay 0.01% but receive 0.05% = net gain of 0.04%.

Since your positions cancel out price movements, you keep that spread as pure profit.`
      },
      {
        id: 'why-use-cactusfund',
        question: "Why should I use CactusFund?",
        answer: `Without CactusFund:
- You'd have to check rates on 10+ exchanges manually
- Compare spreads, fees, and liquidation risks yourself
- React fast before opportunities disappear

With CactusFund:
- All opportunities are sorted by profitability
- Risk levels are calculated automatically
- One-click to see exactly what to do
- Real-time updates every 30 seconds

We do the boring work so you can focus on making money.`
      },
      {
        id: 'is-cactusfund-free',
        question: "Is CactusFund free to use?",
        answer: `The basic features are completely free:
- View all [[funding rates|what-is-funding]] across exchanges
- See top [[arbitrage opportunities|what-is-arb]]
- Learn with our AI assistant

Pro features (coming soon) will include:
- Automated trade execution
- Custom alerts and notifications
- Advanced analytics and reports`
      },
      {
        id: 'is-cactusfund-safe',
        question: "Is CactusFund safe?",
        answer: `Yes! Here's why:
- We NEVER hold your funds (non-custodial)
- Your [[wallet|what-is-wallet]] stays in your control
- We only show you opportunities - YOU decide to trade
- No private keys stored on our servers

Think of CactusFund like Google Maps: we show you the best route, but you're the one driving.`
      }
    ]
  },
  {
    id: 'basics',
    title: 'Crypto Basics',
    icon: 'lucide:book-open',
    questions: [
      {
        id: 'what-is-crypto',
        question: "What is cryptocurrency trading?",
        answer: `Crypto trading means buying and selling digital currencies like Bitcoin or Ethereum to make profit.

With CactusFund, you don't need to predict if prices go up or down - we use a smarter strategy called [[arbitrage|what-is-arb]].`
      },
      {
        id: 'what-is-wallet',
        question: "What is a crypto wallet and how do I set one up?",
        answer: `A crypto wallet is your digital bank account for crypto.

It has a public address (safe to share) and a private key (never share this one).

Popular options include MetaMask, Rainbow, or Trust Wallet. Just click "Connect Wallet" to get started.`
      },
      {
        id: 'what-is-dex',
        question: "What is a DEX (Decentralized Exchange)?",
        answer: `DEX stands for Decentralized Exchange.

Unlike Coinbase or Binance, DEXs run on blockchain: you keep control of your funds, no sign-up required, and you can trade 24/7.

We support the most liquid and reliable DEXs on the market.`
      }
    ]
  },
  {
    id: 'funding',
    title: 'Funding Rates Explained',
    icon: 'lucide:percent',
    questions: [
      {
        id: 'what-is-funding',
        question: "What are funding rates in crypto perpetual futures?",
        answer: `Funding rates are payments between traders that happen every 8 hours.

If too many traders bet UP (long), they pay those betting DOWN (short) and vice versa.

Positive rate = longs pay shorts.
Negative rate = shorts pay longs.

This mechanism keeps perpetual futures prices aligned with spot prices.`
      },
      {
        id: 'why-rates-differ',
        question: "Why are funding rates different on each exchange?",
        answer: `Each exchange has different traders with different opinions.

For example: Exchange A might be -0.05% while Exchange B is +0.03%.

This difference is exactly the opportunity we exploit with funding rate arbitrage!`
      },
      {
        id: 'when-funding-paid',
        question: "When are funding rates paid?",
        answer: `Most exchanges pay funding every 8 hours at specific times (00:00, 08:00, 16:00 UTC).

Some exchanges pay hourly, providing more granular opportunities.

You must hold your position when funding is paid to receive (or pay) the rate.`
      }
    ]
  },
  {
    id: 'arbitrage',
    title: 'Arbitrage Strategy',
    icon: 'lucide:zap',
    questions: [
      {
        id: 'what-is-arb',
        question: "How does funding rate arbitrage work step by step?",
        answer: `Open a LONG where you GET paid, and a SHORT where you also GET paid (or pay less).

Same size, opposite directions = price movements cancel out.

But you still collect funding from BOTH sides.

Example with $10,000 each side:
- Long on Exchange A: receive 0.05% = +$5
- Short on Exchange B: receive 0.03% = +$3
- Total every 8 hours: $8 profit

That's $24/day or $720/month from a $20,000 total position.`
      },
      {
        id: 'how-much-profit',
        question: "How much money can I make with funding arbitrage?",
        answer: `Returns vary with market conditions:

Conservative estimate:
$10,000 position at 0.1% daily = $10/day or $300/month

During high volatility:
0.3% to 1% daily returns are possible ($30-100/day per $10k)

Annual returns can range from 36% (0.1% daily) to 365%+ (1% daily) depending on market conditions and your position size.`
      },
      {
        id: 'what-risks',
        question: "What are the risks of funding rate arbitrage?",
        answer: `Main risks include:
- **Execution risk**: Timing both trades simultaneously
- **Rate changes**: Funding rates can flip before next payment
- **Liquidation**: If one side moves too fast (use max 5x leverage)
- **Smart contract risk**: Rare on major DEXs but possible
- **Exchange issues**: Downtime, withdrawal problems

Start small and learn the mechanics first. Never invest more than you can afford to lose.`
      },
      {
        id: 'delta-neutral',
        question: "What does delta-neutral mean?",
        answer: `Delta-neutral means your overall position has no directional exposure to price.

If BTC goes up 10%, your long position gains and your short position loses - they cancel out.

You're protected from price moves in either direction, allowing you to focus purely on collecting funding rate spreads.`
      }
    ]
  },
  {
    id: 'getting-started',
    title: 'Getting Started Guide',
    icon: 'lucide:rocket',
    questions: [
      {
        id: 'what-need',
        question: "What do I need to start funding rate arbitrage?",
        answer: `You'll need:
- A crypto wallet (MetaMask is recommended)
- Some ETH or USDC ($500+ to start practically)
- $10-20 for blockchain gas fees

Steps to get started:
1. Install MetaMask browser extension
2. Create a new wallet and save your seed phrase OFFLINE
3. Buy ETH on an exchange and transfer to your wallet
4. Connect your wallet to CactusFund
5. Start with a small position to learn the mechanics`
      },
      {
        id: 'how-use-app',
        question: "How do I use the CactusFund platform?",
        answer: `**Dashboard** shows current [[funding rates|what-is-funding]] across all [[DEXs|what-is-dex]].

**Opportunities** page shows the best trades sorted by daily return.

Click an opportunity to see the [[Long/Short pair|what-is-arb]] and execute both trades.

**Pro tip**: Look for daily returns above 0.1% - these are typically the most profitable opportunities worth your time.`
      },
      {
        id: 'common-mistakes',
        question: "What mistakes should beginners avoid?",
        answer: `Common mistakes to avoid:
- Going all-in (start with 10-20% of your capital maximum)
- Ignoring trading fees and gas costs
- Using high leverage (5x maximum is recommended)
- Not monitoring positions regularly
- FOMO trading every opportunity you see
- Forgetting about tax implications
- NEVER share your seed phrase with anyone

The best traders are patient and disciplined. Start slow, learn the mechanics, then scale up.`
      }
    ]
  }
]

// Computed: total questions for stats
const totalQuestions = computed(() => {
  return faqCategories.reduce((acc, cat) => acc + cat.questions.length, 0)
})
</script>

<template>
  <div class="min-h-screen relative overflow-hidden">
    <!-- Global Background Effects -->
    <div class="absolute top-0 left-0 w-full h-[600px] pointer-events-none z-0">
      <div class="absolute top-[5%] left-1/2 -translate-x-1/2 w-[500px] h-[500px] bg-emerald-500/10 rounded-full blur-[100px] opacity-50"></div>
      <div class="absolute top-[15%] left-1/2 -translate-x-1/2 w-[400px] h-[400px] bg-blue-500/8 rounded-full blur-[80px] opacity-40"></div>
    </div>

    <!-- Hero Section -->
    <section class="relative z-10 pt-8 pb-10 sm:pt-12 sm:pb-14">
      <div class="max-w-5xl mx-auto text-center px-4">

        <h1 class="text-3xl sm:text-4xl font-bold text-white mb-3 leading-tight">
          Master
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-green-400 to-teal-400">
            Funding Rate Arbitrage
          </span>
        </h1>
        
        <p class="text-neutral-400 text-sm sm:text-base max-w-xl mx-auto mb-6 leading-relaxed">
          The complete guide to earning passive income from crypto. Learn how delta-neutral strategies 
          can generate <strong class="text-white">0.1-1% daily returns</strong> with minimal risk.
        </p>

        <!-- Quick Action Buttons -->
        <div class="flex flex-col sm:flex-row justify-center items-center gap-3 mb-8">
          <NuxtLink 
            to="/opportunites" 
            class="group w-full sm:w-auto min-w-[200px] flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-gradient-to-r from-emerald-500 to-green-600 text-white text-sm font-semibold hover:from-emerald-600 hover:to-green-700 transition-all shadow-lg shadow-emerald-500/25"
          >
            <span class="iconify" data-icon="lucide:zap" data-width="16"></span>
            View Live Opportunities
            <span class="iconify group-hover:translate-x-1 transition-transform" data-icon="lucide:arrow-right" data-width="14"></span>
          </NuxtLink>
          <button 
            @click="openAndScrollTo('what-is-arb')"
            class="w-full sm:w-auto min-w-[200px] flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-white/5 border border-white/10 text-white text-sm font-medium hover:bg-white/10 hover:border-white/20 transition-all"
          >
            <span class="iconify" data-icon="lucide:book-open" data-width="16"></span>
            Learn the Strategy
          </button>
        </div>

        <!-- Stats Bar -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 max-w-4xl mx-auto w-full">
          <div class="glass-card p-3 rounded-xl text-center group hover:border-emerald-500/30 transition-all">
            <div class="flex items-center justify-center gap-1.5 mb-0.5">
              <span class="iconify text-emerald-400" data-icon="lucide:trending-up" data-width="16"></span>
              <span class="text-base sm:text-lg font-bold text-white">0.1-1%</span>
            </div>
            <span class="text-[10px] text-neutral-500 uppercase tracking-wider">Daily Returns</span>
          </div>
          <div class="glass-card p-3 rounded-xl text-center group hover:border-blue-500/30 transition-all">
            <div class="flex items-center justify-center gap-1.5 mb-0.5">
              <span class="iconify text-blue-400" data-icon="lucide:shield" data-width="16"></span>
              <span class="text-base sm:text-lg font-bold text-white">Low</span>
            </div>
            <span class="text-[10px] text-neutral-500 uppercase tracking-wider">Risk Level</span>
          </div>
          <div class="glass-card p-3 rounded-xl text-center group hover:border-purple-500/30 transition-all">
            <div class="flex items-center justify-center gap-1.5 mb-0.5">
              <span class="iconify text-purple-400" data-icon="lucide:clock" data-width="16"></span>
              <span class="text-base sm:text-lg font-bold text-white">24/7</span>
            </div>
            <span class="text-[10px] text-neutral-500 uppercase tracking-wider">Earning</span>
          </div>
          <div class="glass-card p-3 rounded-xl text-center group hover:border-orange-500/30 transition-all">
            <div class="flex items-center justify-center gap-1.5 mb-0.5">
              <span class="iconify text-orange-400" data-icon="lucide:help-circle" data-width="16"></span>
              <span class="text-base sm:text-lg font-bold text-white">{{ totalQuestions }}+</span>
            </div>
            <span class="text-[10px] text-neutral-500 uppercase tracking-wider">Questions</span>
          </div>
        </div>
      </div>
    </section>

    <!-- How It Works Section -->
    <section class="py-12 px-4" id="how-it-works">
      <div class="max-w-5xl mx-auto">
        <div class="text-center mb-10">
          <h2 class="text-2xl sm:text-3xl font-bold text-white mb-3">
            How Funding Rate Arbitrage Works
          </h2>
          <p class="text-neutral-400 text-sm sm:text-base max-w-xl mx-auto">
            A simple 4-step process to earn passive income from crypto markets
          </p>
        </div>

        <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <article 
            v-for="step in howItWorksSteps" 
            :key="step.step"
            class="group glass-card rounded-2xl p-5 relative overflow-hidden hover:border-white/20 transition-all duration-300"
          >
            <!-- Step Number Badge -->
            <div :class="[
              'absolute -top-2 -right-2 w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold text-white',
              `bg-gradient-to-br from-${step.color}-500 to-${step.color}-600`
            ]" :style="`background: linear-gradient(135deg, var(--color-${step.color}-500), var(--color-${step.color}-600))`">
              {{ step.step }}
            </div>

            <!-- Icon -->
            <div :class="[
              'w-12 h-12 rounded-xl flex items-center justify-center mb-4 transition-transform group-hover:scale-110',
              `bg-${step.color}-500/10`
            ]" :style="`background: rgba(var(--color-${step.color}-500-rgb), 0.1)`">
              <span :class="[`text-${step.color}-400`]" class="iconify" :data-icon="step.icon" data-width="24" :style="`color: var(--color-${step.color}-400)`"></span>
            </div>

            <h3 class="text-base font-semibold text-white mb-2">{{ step.title }}</h3>
            <p class="text-sm text-neutral-400 leading-relaxed">{{ step.description }}</p>
          </article>
        </div>

        <!-- Visual Example -->
        <div class="mt-8 glass-card rounded-2xl p-6 sm:p-8">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <span class="iconify text-emerald-400" data-icon="lucide:calculator" data-width="20"></span>
            Example: $20,000 Position
          </h3>
          
          <div class="grid sm:grid-cols-3 gap-4">
            <!-- Long Position -->
            <div class="bg-red-500/10 border border-red-500/20 rounded-xl p-4 relative group/long">
              <div class="flex items-center gap-2 mb-2">
                <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                <span class="text-xs font-medium text-emerald-400 uppercase tracking-wider">Long Position</span>
                <!-- Tooltip trigger -->
                <div class="relative ml-auto">
                  <span class="w-4 h-4 rounded-full bg-white/10 flex items-center justify-center text-[10px] text-neutral-400 cursor-help hover:bg-white/20 hover:text-white transition-all">?</span>
                  <!-- Tooltip -->
                  <div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 p-2 rounded-lg bg-neutral-900 border border-white/10 text-xs text-neutral-300 opacity-0 invisible group-hover/long:opacity-100 group-hover/long:visible transition-all duration-200 z-20 shadow-xl">
                    <span class="text-emerald-400 font-medium">Green label</span> = Long position type.<br/>
                    <span class="text-red-400 font-medium">Red card</span> = You pay funding here.
                    <div class="absolute top-full left-1/2 -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-neutral-900"></div>
                  </div>
                </div>
              </div>
              <p class="text-white font-semibold">$10,000 on Exchange A</p>
              <p class="text-sm text-neutral-400">Pay -0.02% = <span class="text-red-400">-$2</span></p>
            </div>

            <!-- Arrow -->
            <div class="hidden sm:flex items-center justify-center">
              <div class="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center">
                <span class="iconify text-neutral-500" data-icon="lucide:plus" data-width="24"></span>
              </div>
            </div>

            <!-- Short Position -->
            <div class="bg-emerald-500/10 border border-emerald-500/20 rounded-xl p-4 relative group/short">
              <div class="flex items-center gap-2 mb-2">
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                <span class="text-xs font-medium text-red-400 uppercase tracking-wider">Short Position</span>
                <!-- Tooltip trigger -->
                <div class="relative ml-auto">
                  <span class="w-4 h-4 rounded-full bg-white/10 flex items-center justify-center text-[10px] text-neutral-400 cursor-help hover:bg-white/20 hover:text-white transition-all">?</span>
                  <!-- Tooltip -->
                  <div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 p-2 rounded-lg bg-neutral-900 border border-white/10 text-xs text-neutral-300 opacity-0 invisible group-hover/short:opacity-100 group-hover/short:visible transition-all duration-200 z-20 shadow-xl">
                    <span class="text-red-400 font-medium">Red label</span> = Short position type.<br/>
                    <span class="text-emerald-400 font-medium">Green card</span> = You receive funding here.
                    <div class="absolute top-full left-1/2 -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-neutral-900"></div>
                  </div>
                </div>
              </div>
              <p class="text-white font-semibold">$10,000 on Exchange B</p>
              <p class="text-sm text-neutral-400">Receive +0.10% = <span class="text-emerald-400">+$10</span></p>
            </div>
          </div>

          <!-- Result -->
          <div class="mt-4 p-4 rounded-xl bg-gradient-to-r from-emerald-500/10 to-green-500/10 border border-emerald-500/20">
            <div class="flex items-center justify-between flex-wrap gap-4">
              <div>
                <p class="text-neutral-400 text-sm">Every 8 Hours</p>
                <p class="text-xl font-bold text-emerald-400">+$8 Net Profit</p>
                <p class="text-xs text-neutral-500">(-$2 paid + $10 received)</p>
              </div>
              <div class="text-right">
                <p class="text-neutral-400 text-sm">Daily / Monthly / Yearly</p>
                <p class="text-white font-semibold">$24 / $720 / <span class="text-emerald-400">$8,640</span></p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Benefits Section -->
    <section class="py-12 px-4">
      <div class="max-w-5xl mx-auto">
        <div class="text-center mb-10">
          <h2 class="text-2xl sm:text-3xl font-bold text-white mb-3">
            Why Use CactusFund?
          </h2>
          <p class="text-neutral-400 text-sm sm:text-base max-w-xl mx-auto">
            Everything you need for successful funding rate arbitrage
          </p>
        </div>

        <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <article 
            v-for="(benefit, index) in benefits" 
            :key="index"
            class="glass-card rounded-2xl p-5 group hover:border-emerald-500/30 transition-all duration-300"
          >
            <div class="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <span class="iconify text-emerald-400" :data-icon="benefit.icon" data-width="20"></span>
            </div>
            <h3 class="text-base font-semibold text-white mb-1">{{ benefit.title }}</h3>
            <p class="text-sm text-neutral-400">{{ benefit.description }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- FAQ Section -->
    <section class="py-12 px-4" id="faq">
      <div class="max-w-5xl mx-auto">
        <div class="text-center mb-10">
          <h2 class="text-2xl sm:text-3xl font-bold text-white mb-3">
            Frequently Asked Questions
          </h2>
          <p class="text-neutral-400 text-sm sm:text-base max-w-xl mx-auto">
            Everything you need to know about funding rate arbitrage and CactusFund
          </p>
        </div>

        <!-- FAQ Categories -->
        <div class="space-y-4">
          <article 
            v-for="category in faqCategories" 
            :key="category.id"
            class="glass-card rounded-2xl overflow-hidden"
          >
            <!-- Category Header -->
            <header class="px-4 py-3 border-b border-white/5 flex items-center gap-3">
              <span class="iconify text-emerald-400" :data-icon="category.icon" data-width="18"></span>
              <h3 class="text-base font-semibold text-white">{{ category.title }}</h3>
              <span class="ml-auto text-xs text-neutral-500">{{ category.questions.length }} questions</span>
            </header>

            <!-- Questions -->
            <div class="divide-y divide-white/5">
              <div 
                v-for="q in category.questions" 
                :key="q.id"
                :id="`question-${q.id}`"
                class="group transition-all duration-300"
              >
                <!-- Question Button -->
                <button
                  @click="toggleSection(q.id)"
                  class="w-full px-4 py-3 flex items-center gap-3 text-left hover:bg-white/5 transition-colors"
                  :aria-expanded="isOpen(q.id)"
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
                  <div class="ml-8 p-4 rounded-xl bg-white/[0.02] border border-white/5 text-sm text-neutral-300 leading-relaxed whitespace-pre-line faq-answer">
                    <span v-html="parseAnswer(q.answer)"></span>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="py-12 px-4">
      <div class="max-w-5xl mx-auto">
        <div class="glass-card rounded-3xl p-8 sm:p-12 relative overflow-hidden">
          <!-- Background Gradient -->
          <div class="absolute inset-0 bg-gradient-to-br from-emerald-500/10 via-transparent to-blue-500/10 pointer-events-none"></div>
          <div class="absolute top-0 right-0 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none"></div>

          <div class="relative text-center">
            <h2 class="text-2xl sm:text-3xl font-bold text-white mb-4">
              Ready to Start Earning?
            </h2>
            <p class="text-neutral-400 text-base max-w-xl mx-auto mb-8">
              Join thousands of traders using CactusFund to find the best funding rate arbitrage opportunities.
            </p>

            <div class="flex flex-wrap justify-center gap-4">
              <NuxtLink 
                to="/opportunites" 
                class="group flex items-center gap-2 px-8 py-4 rounded-xl bg-gradient-to-r from-emerald-500 to-green-600 text-white font-semibold hover:from-emerald-600 hover:to-green-700 transition-all shadow-lg shadow-emerald-500/25"
              >
                <span class="iconify" data-icon="lucide:rocket" data-width="20"></span>
                View Live Opportunities
                <span class="iconify group-hover:translate-x-1 transition-transform" data-icon="lucide:arrow-right" data-width="18"></span>
              </NuxtLink>
            </div>

            <!-- Trust Indicators -->
            <div class="mt-8 flex flex-wrap justify-center items-center gap-6 text-neutral-500 text-xs">
              <span class="flex items-center gap-1">
                <span class="iconify" data-icon="lucide:shield-check" data-width="14"></span>
                Non-Custodial
              </span>
              <span class="flex items-center gap-1">
                <span class="iconify" data-icon="lucide:lock" data-width="14"></span>
                Secure
              </span>
              <span class="flex items-center gap-1">
                <span class="iconify" data-icon="lucide:zap" data-width="14"></span>
                Real-Time Data
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- AI Chat Button (Fixed) -->
    <div class="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-2">
      <!-- Chat Hint Tooltip -->
      <div 
        v-if="showChatHint && !chatOpen"
        class="glass-card px-4 py-2 rounded-xl border border-purple-500/30 shadow-lg animate-bounce-slow"
      >
        <div class="flex items-center gap-2">
          <span class="iconify text-purple-400" data-icon="lucide:sparkles" data-width="14"></span>
          <span class="text-sm text-white">Have questions? Ask our AI</span>
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
        aria-label="Open AI Assistant"
      >
        <span class="iconify text-white" :data-icon="chatOpen ? 'lucide:plus' : 'lucide:message-circle'" data-width="24"></span>
      </button>
    </div>

    <!-- AI Chat Panel -->
    <aside 
      v-show="chatOpen"
      class="fixed bottom-24 right-6 w-80 sm:w-96 h-[450px] glass-card rounded-2xl shadow-2xl border border-white/10 flex flex-col overflow-hidden z-50"
      aria-label="AI Chat Assistant"
    >
      <!-- Chat Header -->
      <header class="px-4 py-3 border-b border-white/10 flex items-center gap-3">
        <div class="w-8 h-8 rounded-full bg-gradient-to-r from-purple-500 to-indigo-600 flex items-center justify-center">
          <span class="iconify text-white" data-icon="lucide:bot" data-width="18"></span>
        </div>
        <div class="flex-1">
          <h3 class="text-sm font-semibold text-white">AI Assistant</h3>
          <p class="text-[10px] text-neutral-500">Powered by Mistral AI</p>
        </div>
        <button @click="toggleChat" class="text-neutral-500 hover:text-white transition-colors" aria-label="Close chat">
          <span class="iconify" data-icon="lucide:x" data-width="18"></span>
        </button>
      </header>

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
            aria-label="Send message"
          >
            <span class="iconify" data-icon="lucide:send" data-width="16"></span>
          </button>
        </div>
      </div>
    </aside>
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

/* FAQ Link styles */
:deep(.faq-link) {
  color: #10b981;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 2px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: none;
  border: none;
  padding: 0;
  font: inherit;
}

:deep(.faq-link:hover) {
  color: #34d399;
  text-decoration-style: solid;
}

/* Highlight flash animation */
@keyframes highlight-flash {
  0%, 100% {
    background-color: transparent;
  }
  50% {
    background-color: rgba(16, 185, 129, 0.15);
  }
}

.highlight-flash {
  animation: highlight-flash 0.5s ease-in-out 3;
  border-radius: 0.5rem;
}

/* Color variables for dynamic styling */
:root {
  --color-emerald-400: #34d399;
  --color-emerald-500: #10b981;
  --color-emerald-600: #059669;
  --color-blue-400: #60a5fa;
  --color-blue-500: #3b82f6;
  --color-blue-600: #2563eb;
  --color-purple-400: #a78bfa;
  --color-purple-500: #8b5cf6;
  --color-purple-600: #7c3aed;
  --color-orange-400: #fb923c;
  --color-orange-500: #f97316;
  --color-orange-600: #ea580c;
}
</style>
