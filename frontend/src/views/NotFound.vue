<script setup>
</script>

<template>
  <div class="h-full flex flex-col items-center justify-center text-center">
    <div class="w-24 h-24 bg-white/5 rounded-full flex items-center justify-center mb-6">
      <span class="text-4xl">🤔</span>
    </div>
    <h1 class="text-4xl font-bold text-white mb-2">404</h1>
    <p class="text-neutral-400 mb-8">Page introuvable</p>
    <router-link 
      to="/" 
      class="px-6 py-3 bg-white text-black font-medium rounded-xl hover:bg-neutral-200 transition-colors"
    >
      Retour au Dashboard
    </router-link>
  </div>
</template>
