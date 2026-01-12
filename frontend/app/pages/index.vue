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
  robots: 'index, follow',
})

useHead({
  link: [{ rel: 'canonical', href: 'https://cactusfund.app/' }],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": "CactusFund",
        "description": "Real-time funding rate scanner across top DEXs. Find arbitrage opportunities, compare rates, and maximize crypto trading profits.",
        "url": "https://cactusfund.app",
        "potentialAction": {
          "@type": "SearchAction",
          "target": "https://cactusfund.app/asset?symbol={search_term_string}",
          "query-input": "required name=search_term_string"
        }
      })
    }
  ]
})
</script>
