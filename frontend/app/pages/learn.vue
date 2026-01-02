<script setup>
// Nuxt auto-imports: ref, nextTick, computed

// ===== ENHANCED SEO =====
useSeoMeta({
  title: 'Learn - CactusFund',
  description: 'Master funding rate arbitrage with our complete guide. Learn how to earn 0.1-1% daily returns with low-risk crypto strategies on top DEXs.',
  ogTitle: 'Learn - CactusFund',
  ogDescription: 'The ultimate guide to funding rate arbitrage. Discover how to earn passive income from crypto with delta-neutral strategies.',
  ogUrl: 'https://cactusfund.app/learn',
  ogType: 'article',
  twitterCard: 'summary_large_image',
  robots: 'index, follow',
})

useHead({
  link: [{ rel: 'canonical', href: 'https://cactusfund.app/learn' }]
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

// Parse answer text: convert [[term|questionId]] to links AND markdown to HTML
const parseAnswer = (answer) => {
  let html = answer
  
  // 1. Handle FAQ links first: [[text|id]] -> clickable button
  html = html.replace(/\[\[([^|\]]+)\|([^\]]+)\]\]/g, (match, text, id) => {
    return `<button class="faq-link" onclick="window.openFaqSection('${id}')">${text}</button>`
  })
  
  // 2. Convert markdown to HTML
  
  // Bold: **text** or __text__
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong class="text-white">$1</strong>')
  html = html.replace(/__([^_]+)__/g, '<strong class="text-white">$1</strong>')
  
  // Italic: _text_ (single underscore)
  html = html.replace(/(?<![a-zA-Z])_([^_]+)_(?![a-zA-Z])/g, '<em class="text-neutral-400 italic">$1</em>')
  
  // Headers: ### text (convert to styled span)
  html = html.replace(/^### (.+)$/gm, '<span class="block text-white font-semibold mt-4 mb-2">$1</span>')
  html = html.replace(/^## (.+)$/gm, '<span class="block text-white font-bold text-lg mt-4 mb-2">$1</span>')
  
  // Horizontal rule: ---
  html = html.replace(/^---$/gm, '<hr class="border-white/10 my-3" />')
  
  // Numbered lists: 1. text, 2. text, etc.
  html = html.replace(/^(\d+)\. (.+)$/gm, '<div class="flex gap-2 mt-1"><span class="text-emerald-400 font-medium">$1.</span><span>$2</span></div>')
  
  // Bullet lists with dash: - text (at start of line)
  html = html.replace(/^- (.+)$/gm, '<div class="flex gap-2 mt-1"><span class="text-neutral-500">•</span><span>$1</span></div>')
  
  // Indented bullet lists (3+ spaces followed by dash)
  html = html.replace(/^   - (.+)$/gm, '<div class="flex gap-2 mt-1 ml-4"><span class="text-neutral-600">◦</span><span class="text-neutral-400">$1</span></div>')
  
  // Checkbox unchecked: ☐ text
  html = html.replace(/^☐ (.+)$/gm, '<div class="flex gap-2 mt-1"><span class="text-neutral-500">☐</span><span>$1</span></div>')
  
  // Code/monospace: `text`
  html = html.replace(/`([^`]+)`/g, '<code class="px-1.5 py-0.5 rounded bg-white/10 text-emerald-400 text-xs font-mono">$1</code>')
  
  // Convert double newlines to paragraph breaks
  html = html.replace(/\n\n/g, '</p><p class="mt-3">')
  
  // Wrap in paragraph
  html = '<p>' + html + '</p>'
  
  return html
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
    title: 'We Scan For You',
    description: 'We check prices on multiple exchanges every 10 seconds to find opportunities. You just sit back.',
    icon: 'lucide:scan',
    color: 'emerald'
  },
  {
    step: 2,
    title: 'Spot The Difference',
    description: 'Different exchanges pay different rates. We find where you can earn more than you pay.',
    icon: 'lucide:target',
    color: 'blue'
  },
  {
    step: 3,
    title: 'Balance Your Bets',
    description: 'Open two opposite trades (one "buy", one "sell") on different exchanges. They cancel each other out.',
    icon: 'lucide:git-branch',
    color: 'purple'
  },
  {
    step: 4,
    title: 'Earn While You Sleep',
    description: 'Price goes up or down? Doesn\'t matter. You earn the rate difference every 8 hours.',
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
        answer: `While funding rate arbitrage is considered low-risk, it's NOT risk-free. Here are the main risks you should understand:

⚠️ **[[Rate Reversal Risk|risk-rate-reversal]]** - Funding rates can change direction
⚠️ **[[Platform & Security Risk|risk-platform-hack]]** - Exchange hacks or failures
⚠️ **[[Liquidation Risk|risk-liquidation]]** - Getting liquidated without hedging

Click each risk above to learn more about it and how to protect yourself.

**Golden rule**: Start small, learn the mechanics, and never invest more than you can afford to lose.`
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
    id: 'risks',
    title: 'Risks & Security',
    icon: 'lucide:shield-alert',
    questions: [
      {
        id: 'risk-rate-reversal',
        question: "What is rate reversal risk and how can it affect my profits?",
        answer: `**Rate reversal** happens when the funding rate direction changes while you're holding a position.

📊 **Simple example:**
- Day 1: You go LONG on Exchange A (rate +0.05%) and SHORT on Exchange B (rate -0.02%)
- You earn +0.05% - 0.02% = **+0.03% profit**

- Day 2: Rates flip! Exchange A is now -0.03% and Exchange B is +0.04%
- Now you're PAYING -0.03% + 0.04% = **-0.01% loss**

If you keep your position too long after rates reverse, your profits will slowly disappear!

🛡️ **How to protect yourself:**
1. **Monitor rates regularly** - Check at least once per day
2. **Set alerts** - Use CactusFund notifications (coming soon)
3. **Don't be greedy** - Close positions when spread becomes too small
4. **Be ready to act** - Have a plan to exit or reverse your positions

Remember: Funding rates change based on market sentiment. During volatile periods, they can flip quickly!`
      },
      {
        id: 'risk-platform-hack',
        question: "What happens if a DEX gets hacked? Platform security risks explained",
        answer: `**Platform risk** is the possibility that something goes wrong with the exchange itself, not your trading strategy.

🔓 **Types of platform risks:**

1. **Smart Contract Hacks**
   - DEXs run on code (smart contracts) that could have bugs
   - Hackers can exploit these bugs to steal funds
   - Even audited contracts aren't 100% safe

2. **Exchange Insolvency**
   - The exchange runs out of money (like FTX in 2022)
   - Your funds could be frozen or lost

3. **Withdrawal Issues**
   - Exchange might pause withdrawals
   - Network congestion can delay transactions

4. **Oracle Manipulation**
   - Price feeds can be manipulated
   - Could cause unfair liquidations

🛡️ **How to protect yourself:**
1. **Spread across multiple DEXs** - Don't put all eggs in one basket
2. **Use established DEXs** - Hyperliquid, Paradex, etc. have track records
3. **Only deposit what you need** - Keep most funds in your personal wallet
4. **Monitor DEX health** - Check TVL, audit reports, team reputation
5. **Have an exit plan** - Know how to withdraw quickly if needed

_Note: CactusFund is non-custodial. We never hold your funds - you're trading directly on the DEXs._`
      },
      {
        id: 'risk-liquidation',
        question: "What is liquidation risk and how do take-profit orders protect me?",
        answer: `**Liquidation** happens when your position loses too much money and the exchange forcibly closes it to prevent further losses.

📉 **How liquidation works (for beginners):**

When you open a leveraged position, you put down "margin" (collateral). If your losses approach this margin, you get liquidated.

Example with 5x leverage:
- You deposit $1,000 and open a $5,000 position
- If the price moves 20% against you, you lose $1,000 (your entire margin)
- The exchange liquidates you = position closed + margin gone

⚠️ **The BIG hidden risk in arbitrage:**

You have TWO positions (long + short). If one gets liquidated:
- Your "hedge" is gone
- You're now fully exposed to price movement
- The other position can cause massive losses!

**Scenario:**
1. BTC pumps 30% very fast
2. Your SHORT position gets liquidated (loss)
3. Your LONG is still open but you panic and don't close it
4. BTC drops 20% → now your LONG loses too!

🛡️ **How to protect yourself:**

1. **Use low leverage** - Max 5x, ideally 2-3x
2. **Set take-profit on BOTH sides**
   - If one position closes, the other should auto-close too
   - This keeps you delta-neutral even in chaos

3. **Monitor margin levels**
   - Keep margin above 50% if possible
   - Add more collateral when needed

4. **Use stop-losses wisely**
   - Set them WIDER than normal trading
   - You want to avoid unnecessary liquidations

5. **Position sizing**
   - Never risk more than 10-20% of your total capital on one trade

**What is a Take-Profit order?**
An automatic order that closes your position when it reaches a certain profit. Set TP on both positions so if one closes profitably, the other closes too.`
      },
      {
        id: 'risk-summary',
        question: "Quick risk checklist before opening an arbitrage position",
        answer: `✅ **Pre-trade checklist:**

**Capital Management:**
☐ Am I risking less than 20% of my total capital?
☐ Can I afford to lose this entire amount?
☐ Is my leverage 5x or lower?

**Position Setup:**
☐ Are both positions the exact same size?
☐ Have I set take-profit on BOTH positions?
☐ Is my margin level healthy (above 30%)?

**Platform Check:**
☐ Am I using reputable DEXs?
☐ Do I have enough gas for transactions?
☐ Can I access both exchanges right now?

**Monitoring Plan:**
☐ Will I check rates at least once per day?
☐ Do I know how to close both positions quickly?
☐ Have I bookmarked both exchange interfaces?

**Exit Strategy:**
☐ At what spread will I close the position?
☐ How will I react if rates reverse?
☐ What's my maximum loss tolerance?

**Remember:** The goal is consistent, small gains - not home runs. A 0.1% daily return = 44% annually. Don't get greedy!`
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

**Most importantly:** Not understanding the [[risks|what-risks]] before trading. Read our [[Risks & Security|risk-summary]] section!

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

    <!-- BEGINNER TUTORIAL SECTION -->
    <section class="py-12 px-4" id="tutorial">
      <div class="max-w-5xl mx-auto">
        <!-- Section Header -->
        <div class="text-center mb-10">
          <div class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-gradient-to-r from-emerald-500/20 to-green-500/20 border border-emerald-500/30 mb-4">
            <span class="iconify text-emerald-400" data-icon="lucide:graduation-cap" data-width="18"></span>
            <span class="text-sm font-semibold text-emerald-400">Beginner Tutorial</span>
          </div>
          <h2 class="text-2xl sm:text-3xl font-bold text-white mb-3">
            How To Make Money
            <span class="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-green-400">
              Step by Step
            </span>
          </h2>
          <p class="text-neutral-400 text-sm sm:text-base max-w-2xl mx-auto">
            Even if you know nothing about crypto, follow this guide and you can start earning in less than an hour.
          </p>
        </div>

        <!-- Tutorial Steps -->
        <div class="space-y-6">
          
          <!-- STEP 0: Prerequisites -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-blue-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-blue-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                0
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Before You Start</h3>
                <p class="text-xs text-neutral-400">What you need • 5 min read</p>
              </div>
              <span class="iconify text-blue-400" data-icon="lucide:info" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                To do funding rate arbitrage, you'll need:
              </p>
              <div class="grid sm:grid-cols-2 gap-4">
                <div class="p-4 rounded-xl bg-white/5 border border-white/5">
                  <div class="flex items-center gap-3 mb-2">
                    <span class="iconify text-purple-400" data-icon="lucide:wallet" data-width="20"></span>
                    <span class="font-medium text-white">A crypto wallet</span>
                  </div>
                  <p class="text-xs text-neutral-400">
                    MetaMask, Rabby, Coinbase Wallet, or Rainbow. Free and takes 2 minutes to install.
                    <br><span class="text-purple-400">→ Download at metamask.io or rabby.io</span>
                  </p>
                </div>
                <div class="p-4 rounded-xl bg-white/5 border border-white/5">
                  <div class="flex items-center gap-3 mb-2">
                    <span class="iconify text-green-400" data-icon="lucide:banknote" data-width="20"></span>
                    <span class="font-medium text-white">Capital (minimum ~$500)</span>
                  </div>
                  <p class="text-xs text-neutral-400">
                    In USDC or ETH. The more capital you have, the more you earn. Start small to learn.
                  </p>
                </div>
                <div class="p-4 rounded-xl bg-white/5 border border-white/5">
                  <div class="flex items-center gap-3 mb-2">
                    <span class="iconify text-cyan-400" data-icon="lucide:monitor" data-width="20"></span>
                    <span class="font-medium text-white">2 browser tabs</span>
                  </div>
                  <p class="text-xs text-neutral-400">
                    One for each exchange. You'll need to act on both at the same time.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- STEP 1: Create Accounts -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-emerald-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-emerald-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                1
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Create Accounts on 2 Platforms</h3>
                <p class="text-xs text-neutral-400">About 10 minutes</p>
              </div>
              <span class="iconify text-emerald-400" data-icon="lucide:user-plus" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                Arbitrage works by exploiting <strong class="text-white">rate differences</strong> between platforms. So you need an account on at least 2 different exchanges.
              </p>
              
              <div class="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                <p class="text-sm text-neutral-300 mb-3">
                  <strong class="text-emerald-400">Good news:</strong> On DEXs (decentralized exchanges), no account creation needed! You simply connect with your MetaMask wallet.
                </p>
              </div>

              <div class="space-y-3">
                <p class="text-xs font-medium text-neutral-500 uppercase tracking-wider">Recommended platforms:</p>
                <div class="grid sm:grid-cols-2 gap-3">
                  <a href="https://app.hyperliquid.xyz" target="_blank" rel="noopener" class="flex items-center gap-3 p-3 rounded-xl bg-white/5 border border-white/10 hover:border-orange-500/30 transition-all group">
                    <img src="/dex-icons/hyperliquid.png" alt="Hyperliquid" class="w-8 h-8 rounded-lg">
                    <div class="flex-1">
                      <span class="text-white font-medium group-hover:text-orange-400 transition-colors">Hyperliquid</span>
                      <p class="text-[10px] text-neutral-500">Best rates, very liquid</p>
                    </div>
                    <span class="iconify text-neutral-500 group-hover:text-orange-400" data-icon="lucide:external-link" data-width="14"></span>
                  </a>
                  <a href="https://app.paradex.trade" target="_blank" rel="noopener" class="flex items-center gap-3 p-3 rounded-xl bg-white/5 border border-white/10 hover:border-green-500/30 transition-all group">
                    <img src="/dex-icons/paradex.png" alt="Paradex" class="w-8 h-8 rounded-lg">
                    <div class="flex-1">
                      <span class="text-white font-medium group-hover:text-green-400 transition-colors">Paradex</span>
                      <p class="text-[10px] text-neutral-500">On Starknet, very low fees</p>
                    </div>
                    <span class="iconify text-neutral-500 group-hover:text-green-400" data-icon="lucide:external-link" data-width="14"></span>
                  </a>
                </div>
              </div>

              <div class="flex items-start gap-3 p-3 rounded-xl bg-white/5">
                <span class="iconify text-yellow-400 mt-0.5" data-icon="lucide:alert-circle" data-width="16"></span>
                <p class="text-xs text-neutral-400">
                  <strong class="text-white">Important:</strong> Connect your wallet to both platforms and deposit funds on each. Half your capital on each.
                </p>
              </div>
            </div>
          </div>

          <!-- STEP 2: Find Opportunity -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-purple-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-purple-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                2
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Find an Opportunity on CactusFund</h3>
                <p class="text-xs text-neutral-400">2 minutes</p>
              </div>
              <span class="iconify text-purple-400" data-icon="lucide:search" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                CactusFund scans all platforms for you. Go to the <strong class="text-emerald-400">Opportunities</strong> page and find an interesting trade.
              </p>

              <div class="p-4 rounded-xl bg-purple-500/10 border border-purple-500/20">
                <p class="text-sm font-medium text-white mb-2">What to look for:</p>
                <ul class="space-y-2 text-sm text-neutral-300">
                  <li class="flex items-start gap-2">
                    <span class="text-emerald-400 mt-1">✓</span>
                    <span><strong class="text-white">Positive spread</strong> — Higher is better (0.1%+ daily = good)</span>
                  </li>
                  <li class="flex items-start gap-2">
                    <span class="text-emerald-400 mt-1">✓</span>
                    <span><strong class="text-white">Platforms where you have funds</strong> — Make sure you can trade on both</span>
                  </li>
                  <li class="flex items-start gap-2">
                    <span class="text-emerald-400 mt-1">✓</span>
                    <span><strong class="text-white">An asset you know</strong> — BTC, ETH, SOL are the simplest</span>
                  </li>
                </ul>
              </div>

              <NuxtLink 
                to="/opportunites"
                class="flex items-center justify-center gap-2 w-full p-3 rounded-xl bg-gradient-to-r from-purple-500 to-indigo-600 text-white font-medium hover:from-purple-600 hover:to-indigo-700 transition-all"
              >
                <span class="iconify" data-icon="lucide:zap" data-width="18"></span>
                View Real-Time Opportunities
                <span class="iconify" data-icon="lucide:arrow-right" data-width="16"></span>
              </NuxtLink>
            </div>
          </div>

          <!-- STEP 3: Open Positions -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-orange-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-orange-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                3
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Open 2 Positions at the Same Time</h3>
                <p class="text-xs text-neutral-400">Be quick! • 2-5 minutes</p>
              </div>
              <span class="iconify text-orange-400" data-icon="lucide:split" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                This is the key step! You'll open <strong class="text-white">two opposite trades</strong> on both platforms. The idea: if the price goes up, you win on one side and lose on the other → it cancels out. But you keep the <strong class="text-emerald-400">funding</strong>!
              </p>

              <div class="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
                <div class="flex items-start gap-3">
                  <span class="iconify text-amber-400 mt-0.5" data-icon="lucide:timer" data-width="18"></span>
                  <div>
                    <p class="text-sm font-medium text-white mb-1">IMPORTANT: Do both trades at the same time!</p>
                    <p class="text-xs text-neutral-300">
                      Open 2 tabs side by side. Prepare both trades, then execute them one after the other within seconds. If you wait too long, the price can move and you lose money.
                    </p>
                  </div>
                </div>
              </div>

              <div class="grid sm:grid-cols-2 gap-4">
                <!-- Long Side -->
                <div class="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                  <div class="flex items-center gap-2 mb-3">
                    <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
                    <span class="text-xs font-bold text-emerald-400 uppercase tracking-wider">Platform A</span>
                  </div>
                  <p class="text-white font-semibold mb-2">Open a LONG</p>
                  <ul class="space-y-1 text-xs text-neutral-300">
                    <li>• Go to the pair (e.g., BTC-USD-PERP)</li>
                    <li>• Click on <span class="text-emerald-400">"Buy"</span> or <span class="text-emerald-400">"Long"</span></li>
                    <li>• Enter the amount (e.g., $500)</li>
                    <li>• Choose your leverage (max 5x recommended)</li>
                    <li>• Confirm the trade</li>
                  </ul>
                </div>
                
                <!-- Short Side -->
                <div class="p-4 rounded-xl bg-red-500/10 border border-red-500/20">
                  <div class="flex items-center gap-2 mb-3">
                    <span class="w-2 h-2 rounded-full bg-red-500"></span>
                    <span class="text-xs font-bold text-red-400 uppercase tracking-wider">Platform B</span>
                  </div>
                  <p class="text-white font-semibold mb-2">Open a SHORT</p>
                  <ul class="space-y-1 text-xs text-neutral-300">
                    <li>• Go to the same pair (BTC-USD-PERP)</li>
                    <li>• Click on <span class="text-red-400">"Sell"</span> or <span class="text-red-400">"Short"</span></li>
                    <li>• <strong class="text-white">SAME amount</strong> (e.g., $500)</li>
                    <li>• <strong class="text-white">SAME leverage</strong> as the other side</li>
                    <li>• Confirm the trade</li>
                  </ul>
                </div>
              </div>

              <div class="flex items-start gap-3 p-3 rounded-xl bg-white/5">
                <span class="iconify text-emerald-400 mt-0.5" data-icon="lucide:check-circle" data-width="16"></span>
                <p class="text-xs text-neutral-400">
                  <strong class="text-white">Well done!</strong> You're now "delta-neutral". If BTC goes up 10%, you win ~$50 on the LONG and lose ~$50 on the SHORT → 0. But you collect funding from both sides!
                </p>
              </div>
            </div>
          </div>

          <!-- STEP 4: Wait & Collect -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-cyan-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-cyan-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                4
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Wait and Collect Your Earnings</h3>
                <p class="text-xs text-neutral-400">Every 8 hours</p>
              </div>
              <span class="iconify text-cyan-400" data-icon="lucide:clock" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                Funding is paid <strong class="text-white">every 8 hours</strong> (00:00, 08:00, 16:00 UTC). As long as you keep your positions open, you collect automatically.
              </p>

              <div class="p-4 rounded-xl bg-gradient-to-r from-emerald-500/10 to-cyan-500/10 border border-emerald-500/20">
                <p class="text-sm font-medium text-white mb-3">Example with $1,000:</p>
                <div class="grid sm:grid-cols-3 gap-3 text-center">
                  <div class="p-3 rounded-lg bg-black/20">
                    <p class="text-xs text-neutral-400 mb-1">Every 8h</p>
                    <p class="text-lg font-bold text-emerald-400">+$0.40</p>
                  </div>
                  <div class="p-3 rounded-lg bg-black/20">
                    <p class="text-xs text-neutral-400 mb-1">Per day</p>
                    <p class="text-lg font-bold text-emerald-400">+$1.20</p>
                  </div>
                  <div class="p-3 rounded-lg bg-black/20">
                    <p class="text-xs text-neutral-400 mb-1">Per month</p>
                    <p class="text-lg font-bold text-emerald-400">+$36</p>
                  </div>
                </div>
                <p class="text-xs text-neutral-500 text-center mt-2">Based on a 0.12% daily spread. Results vary with market conditions.</p>
              </div>

              <div class="flex items-start gap-3 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20">
                <span class="iconify text-amber-400 mt-0.5" data-icon="lucide:eye" data-width="16"></span>
                <div class="text-xs text-neutral-300">
                  <strong class="text-white">Monitor your positions!</strong>
                  <br>Rates change. If the spread stays negative for 2+ days, it's time to close your positions. Check at least 1x per day.
                </div>
              </div>
            </div>
          </div>

          <!-- STEP 5: Close Positions -->
          <div class="glass-card rounded-2xl overflow-hidden">
            <div class="px-5 py-4 bg-gradient-to-r from-pink-500/10 to-transparent border-b border-white/5 flex items-center gap-4">
              <div class="w-10 h-10 rounded-full bg-pink-500 flex items-center justify-center text-white font-bold text-lg shrink-0">
                5
              </div>
              <div class="flex-1">
                <h3 class="text-lg font-semibold text-white">Close Positions (Take Your Profits)</h3>
                <p class="text-xs text-neutral-400">Whenever you want • 2 minutes</p>
              </div>
              <span class="iconify text-pink-400" data-icon="lucide:log-out" data-width="20"></span>
            </div>
            <div class="p-5 space-y-4">
              <p class="text-neutral-300 text-sm">
                When you want to stop (spread becomes small, you need your money, etc.), close both positions at the same time.
              </p>

              <div class="grid sm:grid-cols-2 gap-4">
                <div class="p-4 rounded-xl bg-white/5 border border-white/5">
                  <p class="text-xs font-medium text-neutral-500 uppercase tracking-wider mb-2">On Platform A</p>
                  <p class="text-white font-medium">Close the LONG</p>
                  <p class="text-xs text-neutral-400 mt-1">Click on "Close" or "Sell" to close</p>
                </div>
                <div class="p-4 rounded-xl bg-white/5 border border-white/5">
                  <p class="text-xs font-medium text-neutral-500 uppercase tracking-wider mb-2">On Platform B</p>
                  <p class="text-white font-medium">Close the SHORT</p>
                  <p class="text-xs text-neutral-400 mt-1">Click on "Close" or "Buy to Close"</p>
                </div>
              </div>

              <div class="flex items-start gap-3 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                <span class="iconify text-emerald-400 mt-0.5" data-icon="lucide:party-popper" data-width="16"></span>
                <p class="text-xs text-neutral-300">
                  <strong class="text-emerald-400">That's it!</strong> Your funding earnings are yours. You can withdraw to your wallet or start again with a new opportunity.
                </p>
              </div>
            </div>
          </div>

        </div>

        <!-- Quick Tips Summary -->
        <div class="mt-8 p-6 rounded-2xl bg-gradient-to-r from-indigo-500/10 via-purple-500/10 to-pink-500/10 border border-white/10">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <span class="iconify text-purple-400" data-icon="lucide:lightbulb" data-width="20"></span>
            Summary & Pro Tips
          </h3>
          <div class="grid sm:grid-cols-2 gap-4 text-sm">
            <div class="flex items-start gap-2">
              <span class="text-emerald-400">✓</span>
              <span class="text-neutral-300"><strong class="text-white">Start small</strong> — $500-1000 max to learn</span>
            </div>
            <div class="flex items-start gap-2">
              <span class="text-emerald-400">✓</span>
              <span class="text-neutral-300"><strong class="text-white">Same size on both sides</strong> — This is mandatory</span>
            </div>
            <div class="flex items-start gap-2">
              <span class="text-emerald-400">✓</span>
              <span class="text-neutral-300"><strong class="text-white">Max 5x leverage</strong> — Less is better</span>
            </div>
            <div class="flex items-start gap-2">
              <span class="text-emerald-400">✓</span>
              <span class="text-neutral-300"><strong class="text-white">Check 1x/day</strong> — Rates can change</span>
            </div>
            <div class="flex items-start gap-2">
              <span class="text-amber-400">⚠</span>
              <span class="text-neutral-300"><strong class="text-white">Close if spread stays inverted 2+ days</strong> — Don't rush, but don't ignore trends</span>
            </div>
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

    <!-- Risks Section -->
    <section class="py-12 px-4" id="risks">
      <div class="max-w-5xl mx-auto">
        <div class="text-center mb-10">
          <div class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-amber-500/10 border border-amber-500/20 mb-4">
            <span class="iconify text-amber-400" data-icon="lucide:alert-triangle" data-width="14"></span>
            <span class="text-xs font-medium text-amber-400 uppercase tracking-wider">Important</span>
          </div>
          <h2 class="text-2xl sm:text-3xl font-bold text-white mb-3">
            Understand The Risks
          </h2>
          <p class="text-neutral-400 text-sm sm:text-base max-w-xl mx-auto">
            Funding rate arbitrage is considered low-risk, but it's <strong class="text-white">NOT risk-free</strong>. Learn about these risks before trading.
          </p>
        </div>

        <div class="grid sm:grid-cols-3 gap-4">
          <!-- Risk 1: Rate Reversal -->
          <button 
            @click="openAndScrollTo('risk-rate-reversal')"
            class="glass-card rounded-2xl p-5 text-left group hover:border-amber-500/30 transition-all duration-300 cursor-pointer"
          >
            <div class="w-12 h-12 rounded-xl bg-amber-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
              <span class="iconify text-amber-400" data-icon="lucide:refresh-ccw" data-width="24"></span>
            </div>
            <h3 class="text-base font-semibold text-white mb-2 flex items-center gap-2">
              Rate Reversal
              <span class="iconify text-neutral-500 group-hover:text-amber-400 transition-colors" data-icon="lucide:arrow-right" data-width="14"></span>
            </h3>
            <p class="text-sm text-neutral-400 leading-relaxed mb-3">
              Funding rates can change direction. If you hold too long, profits can turn into losses.
            </p>
            <span class="text-xs text-amber-400 font-medium">Learn how to protect yourself →</span>
          </button>

          <!-- Risk 2: Platform/Hack -->
          <button 
            @click="openAndScrollTo('risk-platform-hack')"
            class="glass-card rounded-2xl p-5 text-left group hover:border-red-500/30 transition-all duration-300 cursor-pointer"
          >
            <div class="w-12 h-12 rounded-xl bg-red-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
              <span class="iconify text-red-400" data-icon="lucide:shield-off" data-width="24"></span>
            </div>
            <h3 class="text-base font-semibold text-white mb-2 flex items-center gap-2">
              Platform Risk
              <span class="iconify text-neutral-500 group-hover:text-red-400 transition-colors" data-icon="lucide:arrow-right" data-width="14"></span>
            </h3>
            <p class="text-sm text-neutral-400 leading-relaxed mb-3">
              Exchanges can get hacked, go offline, or face liquidity issues. Your funds could be at risk.
            </p>
            <span class="text-xs text-red-400 font-medium">Learn how to minimize exposure →</span>
          </button>

          <!-- Risk 3: Liquidation -->
          <button 
            @click="openAndScrollTo('risk-liquidation')"
            class="glass-card rounded-2xl p-5 text-left group hover:border-orange-500/30 transition-all duration-300 cursor-pointer"
          >
            <div class="w-12 h-12 rounded-xl bg-orange-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
              <span class="iconify text-orange-400" data-icon="lucide:zap-off" data-width="24"></span>
            </div>
            <h3 class="text-base font-semibold text-white mb-2 flex items-center gap-2">
              Liquidation Risk
              <span class="iconify text-neutral-500 group-hover:text-orange-400 transition-colors" data-icon="lucide:arrow-right" data-width="14"></span>
            </h3>
            <p class="text-sm text-neutral-400 leading-relaxed mb-3">
              If one position gets liquidated without a take-profit on the other, you lose your hedge.
            </p>
            <span class="text-xs text-orange-400 font-medium">Learn about take-profit protection →</span>
          </button>
        </div>

        <!-- Quick Tip -->
        <div class="mt-6 p-4 rounded-xl bg-gradient-to-r from-amber-500/5 via-transparent to-red-500/5 border border-white/5">
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center flex-shrink-0">
              <span class="iconify text-neutral-400" data-icon="lucide:lightbulb" data-width="16"></span>
            </div>
            <div>
              <p class="text-sm text-neutral-300">
                <strong class="text-white">Golden Rule:</strong> Never invest more than you can afford to lose. Start with small positions and scale up as you learn.
              </p>
              <button 
                @click="openAndScrollTo('risk-summary')"
                class="mt-2 text-xs text-emerald-400 hover:text-emerald-300 font-medium transition-colors"
              >
                View pre-trade checklist →
              </button>
            </div>
          </div>
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
