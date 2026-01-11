<script setup>
useSeoMeta({
  title: 'Accès non autorisé - CactusFund',
  description: 'Vous n\'avez pas les permissions nécessaires pour accéder à cette page.',
})

const { isAuthenticated } = useAuth()
const { hasValidToken } = useSecureAuth()
</script>

<template>
  <div class="min-h-[60vh] flex items-center justify-center">
    <div class="glass-card rounded-3xl p-8 sm:p-12 max-w-lg text-center">
      <!-- Icon -->
      <div class="w-20 h-20 mx-auto mb-6 rounded-2xl bg-red-500/10 flex items-center justify-center">
        <span class="iconify text-red-400" data-icon="lucide:shield-x" data-width="40"></span>
      </div>

      <!-- Title -->
      <h1 class="text-2xl sm:text-3xl font-bold text-white mb-3">
        Accès non autorisé
      </h1>

      <!-- Message -->
      <p class="text-neutral-400 text-sm sm:text-base mb-6">
        <template v-if="!hasValidToken">
          Vous devez vous connecter avec un wallet administrateur pour accéder à cette page.
        </template>
        <template v-else>
          Votre compte n'a pas les permissions nécessaires pour accéder à cette page.
        </template>
      </p>

      <!-- Actions -->
      <div class="flex flex-col sm:flex-row gap-3 justify-center">
        <NuxtLink 
          to="/" 
          class="px-6 py-3 bg-white/10 hover:bg-white/20 text-white rounded-xl text-sm font-medium transition-colors"
        >
          Retour à l'accueil
        </NuxtLink>
        
        <button 
          v-if="!hasValidToken"
          @click="$emit('connectWallet')"
          class="px-6 py-3 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors flex items-center justify-center gap-2"
        >
          <span class="iconify" data-icon="lucide:wallet" data-width="16"></span>
          Connecter un wallet
        </button>
      </div>
    </div>
  </div>
</template>
