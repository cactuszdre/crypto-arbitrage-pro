<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../services/api'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

const handleLogin = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await authApi.login(username.value, password.value)
    localStorage.setItem('user', JSON.stringify(response.data))
    router.push('/')
  } catch (err) {
    error.value = 'Identifiants invalides'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-[#0a0a0a]">
    <div class="glass-card p-8 rounded-3xl w-full max-w-md relative overflow-hidden">
      <!-- Decorator -->
      <div class="absolute top-0 right-0 p-12 opacity-50">
        <div class="w-32 h-32 bg-indigo-500/20 rounded-full blur-3xl"></div>
      </div>

      <div class="relative z-10">
        <h2 class="text-3xl font-bold text-white mb-2">Connexion</h2>
        <p class="text-neutral-400 text-sm mb-8">Accédez à votre tableau de bord crypto</p>
        
        <form @submit.prevent="handleLogin" class="space-y-6">
          <div class="space-y-2">
            <label class="text-xs font-medium text-neutral-300 ml-1">Nom d'utilisateur</label>
            <input 
              v-model="username"
              type="text" 
              required
              class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-neutral-500 focus:outline-none focus:border-indigo-500/50 focus:bg-white/10 transition-all"
              placeholder="Entrez votre nom d'utilisateur"
            >
          </div>

          <div class="space-y-2">
            <label class="text-xs font-medium text-neutral-300 ml-1">Mot de passe</label>
            <input 
              v-model="password"
              type="password" 
              required
              class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white placeholder-neutral-500 focus:outline-none focus:border-indigo-500/50 focus:bg-white/10 transition-all"
              placeholder="••••••••"
            >
          </div>

          <div v-if="error" class="bg-red-500/10 border border-red-500/20 rounded-xl p-3">
            <p class="text-red-400 text-xs text-center">{{ error }}</p>
          </div>

          <button 
            type="submit" 
            :disabled="loading"
            class="w-full bg-white text-black font-semibold py-3 rounded-xl hover:bg-neutral-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <span v-if="loading" class="animate-spin">⏳</span>
            <span>{{ loading ? 'Connexion...' : 'Se connecter' }}</span>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
