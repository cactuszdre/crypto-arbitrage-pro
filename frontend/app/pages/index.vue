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

// SEO with dynamic canonical
useSeoMeta({
  title: 'CactusFund',
  description: 'Real-time funding rate scanner across top DEXs. Find arbitrage opportunities, compare rates, and maximize crypto trading profits.',
  ogTitle: 'CactusFund',
  ogDescription: 'Real-time funding rate scanner across top DEXs. Find arbitrage opportunities and maximize profits.',
  ogUrl: 'https://cactusfund.app/',
})

useHead({
  link: [{ rel: 'canonical', href: 'https://cactusfund.app/' }]
})
</script>
