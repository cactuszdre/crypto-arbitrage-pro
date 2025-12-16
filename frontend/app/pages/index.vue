<template>
  <div class="w-full h-full">
    <CactusFundHero />
  </div>
</template>

<script setup>
// Use landing layout (no sidebar/header)
definePageMeta({
  layout: 'landing'
})

// SEO
useSeoMeta({
  title: 'CactusFund - Real-Time Crypto Funding Rates & Arbitrage Scanner',
  description: 'Track live funding rates across Paradex, Hyperliquid, and Lighter DEXs. Discover arbitrage opportunities and maximize your crypto trading profits.',
})
</script>
