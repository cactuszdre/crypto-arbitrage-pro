<script setup>
import { ref, onMounted } from 'vue'
import { dexApi } from '../services/api'

const dexes = ref([])

const fetchDexes = async () => {
  try {
    const response = await dexApi.getAll()
    dexes.value = response.data.map(d => ({
      ...d,
      status: d.active ? 'active' : 'inactive',
      apiEndpoint: d.baseUrl
    }))
  } catch (err) {
    console.error(err)
  }
}

onMounted(() => {
  fetchDexes()
})

const showAddModal = ref(false)
const newDex = ref({
  name: '',
  apiKey: '',
  apiEndpoint: ''
})

const addDex = async () => {
  try {
    await dexApi.create({
      name: newDex.value.name,
      baseUrl: newDex.value.apiEndpoint,
      apiKey: newDex.value.apiKey
    })
    await fetchDexes()
    showAddModal.value = false
    newDex.value = { name: '', apiKey: '', apiEndpoint: '' }
  } catch (err) {
    console.error('Failed to create DEX', err)
    alert('Failed to create DEX')
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white">Administration DEX</h2>
        <p class="text-neutral-400 text-sm">Gérez les connecteurs et les clés API</p>
      </div>
      <button 
        @click="showAddModal = true"
        class="bg-indigo-500 hover:bg-indigo-600 text-white px-4 py-2 rounded-xl text-sm font-medium transition-colors flex items-center gap-2"
      >
        <span class="iconify" data-icon="lucide:plus" data-width="16"></span>
        Ajouter un DEX
      </button>
    </div>

    <!-- DEX List -->
    <div class="glass-card rounded-3xl overflow-hidden">
      <table class="w-full">
        <thead class="bg-white/5">
          <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
            <th class="px-6 py-4 font-medium">Nom</th>
            <th class="px-6 py-4 font-medium">Endpoint API</th>
            <th class="px-6 py-4 font-medium">Status</th>
            <th class="px-6 py-4 font-medium text-right">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-white/5">
          <tr v-for="dex in dexes" :key="dex.id" class="group hover:bg-white/5 transition-colors">
            <td class="px-6 py-4">
              <span class="text-sm font-medium text-white">{{ dex.name }}</span>
            </td>
            <td class="px-6 py-4">
              <span class="text-xs text-neutral-400 font-mono">{{ dex.apiEndpoint }}</span>
            </td>
            <td class="px-6 py-4">
              <span 
                :class="[
                  'px-2 py-1 rounded-full text-[10px] font-medium border',
                  dex.status === 'active' 
                    ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/10' 
                    : 'bg-neutral-500/10 text-neutral-400 border-neutral-500/10'
                ]"
              >
                {{ dex.status }}
              </span>
            </td>
            <td class="px-6 py-4 text-right">
              <button class="text-neutral-400 hover:text-white transition-colors">
                <span class="iconify" data-icon="lucide:settings" data-width="16"></span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal (Simple implementation) -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <div class="glass-card w-full max-w-lg rounded-3xl p-6 relative">
        <button @click="showAddModal = false" class="absolute top-4 right-4 text-neutral-400 hover:text-white">
          <span class="iconify" data-icon="lucide:x" data-width="20"></span>
        </button>
        
        <h3 class="text-xl font-bold text-white mb-6">Connecter un nouveau DEX</h3>
        
        <form @submit.prevent="addDex" class="space-y-4">
          <div class="space-y-2">
            <label class="text-xs font-medium text-neutral-300">Nom du DEX</label>
            <input v-model="newDex.name" type="text" class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2 text-white focus:outline-none focus:border-indigo-500/50" placeholder="Ex: Binance, Bybit...">
          </div>
          <div class="space-y-2">
            <label class="text-xs font-medium text-neutral-300">Endpoint API</label>
            <input v-model="newDex.apiEndpoint" type="text" class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2 text-white focus:outline-none focus:border-indigo-500/50" placeholder="https://api.exchange.com">
          </div>
          <div class="space-y-2">
            <label class="text-xs font-medium text-neutral-300">API Key</label>
            <input v-model="newDex.apiKey" type="password" class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2 text-white focus:outline-none focus:border-indigo-500/50" placeholder="••••••••">
          </div>
          
          <div class="pt-4 flex justify-end gap-3">
            <button type="button" @click="showAddModal = false" class="px-4 py-2 text-neutral-400 hover:text-white transition-colors text-sm">Annuler</button>
            <button type="submit" class="bg-indigo-500 hover:bg-indigo-600 text-white px-6 py-2 rounded-xl text-sm font-medium transition-colors">Ajouter</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
