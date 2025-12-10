<script setup>
import { ref, onMounted } from 'vue'
import { dexApi, userApi } from '../services/api'
import { useAuth } from '../stores/auth'

const { state: authState } = useAuth()

// ===== DEX Management =====
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

const showAddDexModal = ref(false)
const newDex = ref({ name: '', apiKey: '', apiEndpoint: '' })

const addDex = async () => {
  try {
    await dexApi.create({
      name: newDex.value.name,
      baseUrl: newDex.value.apiEndpoint,
      apiKey: newDex.value.apiKey
    })
    await fetchDexes()
    showAddDexModal.value = false
    newDex.value = { name: '', apiKey: '', apiEndpoint: '' }
  } catch (err) {
    console.error('Failed to create DEX', err)
    alert('Failed to create DEX')
  }
}

// ===== Admin Management =====
const admins = ref([])
const showAddAdminModal = ref(false)
const newAdminWallet = ref('')
const isLoading = ref(false)

const fetchAdmins = async () => {
  try {
    const response = await userApi.getAdmins()
    admins.value = response.data
  } catch (err) {
    console.error('Failed to fetch admins', err)
  }
}

const addAdmin = async () => {
  if (!newAdminWallet.value.trim()) return

  isLoading.value = true
  try {
    await userApi.addAdmin(newAdminWallet.value.trim())
    await fetchAdmins()
    showAddAdminModal.value = false
    newAdminWallet.value = ''
  } catch (err) {
    console.error('Failed to add admin', err)
    alert('Failed to add admin')
  } finally {
    isLoading.value = false
  }
}

const removeAdmin = async (walletAddress) => {
  if (!confirm(`Voulez-vous vraiment retirer les droits admin de ${walletAddress.slice(0, 10)}...?`)) return

  try {
    await userApi.removeAdmin(walletAddress)
    await fetchAdmins()
  } catch (err) {
    console.error('Failed to remove admin', err)
    alert('Failed to remove admin')
  }
}

const formatWallet = (addr) => {
  if (!addr) return ''
  return `${addr.slice(0, 6)}...${addr.slice(-4)}`
}

// Check if the wallet is the current connected user
const isCurrentUser = (walletAddress) => {
  if (!authState.user?.walletAddress || !walletAddress) return false
  return authState.user.walletAddress.toLowerCase() === walletAddress.toLowerCase()
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  fetchDexes()
  fetchAdmins()
})
</script>

<template>
  <div class="space-y-6 sm:space-y-8">
    <!-- ===== ADMIN MANAGEMENT SECTION ===== -->
    <div>
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 mb-4">
        <div>
          <h2 class="text-xl sm:text-2xl font-bold text-white">Gestion des Administrateurs</h2>
          <p class="text-neutral-400 text-xs sm:text-sm">Gérez les accès administrateurs</p>
        </div>
        <button
          @click="showAddAdminModal = true"
          class="bg-purple-500 hover:bg-purple-600 text-white px-3 sm:px-4 py-2 rounded-lg sm:rounded-xl text-xs sm:text-sm font-medium transition-colors flex items-center gap-2 w-full sm:w-auto justify-center"
        >
          <span class="iconify" data-icon="lucide:shield-plus" data-width="16"></span>
          Ajouter un Admin
        </button>
      </div>

      <!-- Admin List -->
      <div class="glass-card rounded-2xl sm:rounded-3xl overflow-hidden overflow-x-auto">
        <table class="w-full min-w-[600px]">
          <thead class="bg-white/5">
            <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Wallet</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Plan</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Créé le</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Dernière connexion</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="admin in admins" :key="admin.id" class="group hover:bg-white/5 transition-colors">
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <div class="flex items-center gap-2">
                  <span class="iconify text-purple-400" data-icon="lucide:shield-check" data-width="16"></span>
                  <span class="text-xs sm:text-sm font-mono text-white">{{ formatWallet(admin.walletAddress) }}</span>
                  <button
                    @click="navigator.clipboard.writeText(admin.walletAddress)"
                    class="text-neutral-500 hover:text-white transition-colors opacity-0 group-hover:opacity-100"
                    title="Copier l'adresse"
                  >
                    <span class="iconify" data-icon="lucide:copy" data-width="12"></span>
                  </button>
                </div>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <span
                  :class="[
                    'px-2 py-1 rounded-full text-[10px] font-medium border',
                    admin.plan === 'PRO'
                      ? 'bg-amber-500/10 text-amber-400 border-amber-500/10'
                      : 'bg-neutral-500/10 text-neutral-400 border-neutral-500/10'
                  ]"
                >
                  {{ admin.plan }}
                </span>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <span class="text-[10px] sm:text-xs text-neutral-400">{{ formatDate(admin.createdAt) }}</span>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <span class="text-[10px] sm:text-xs text-neutral-400">{{ formatDate(admin.lastLoginAt) }}</span>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <!-- Show "Vous" badge for current user -->
                  <span
                    v-if="isCurrentUser(admin.walletAddress)"
                    class="px-2 py-1 rounded-full text-[10px] font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/10"
                  >
                    Vous
                  </span>
                  <!-- Show remove button only for other admins -->
                  <button
                    v-else
                    @click="removeAdmin(admin.walletAddress)"
                    class="text-red-400 hover:text-red-300 transition-colors"
                    title="Retirer les droits admin"
                  >
                    <span class="iconify" data-icon="lucide:user-minus" data-width="16"></span>
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="admins.length === 0">
              <td colspan="5" class="px-4 sm:px-6 py-6 sm:py-8 text-center text-neutral-500 text-xs sm:text-sm">
                Aucun administrateur trouvé
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== DEX MANAGEMENT SECTION ===== -->
    <div>
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 mb-4">
        <div>
          <h2 class="text-xl sm:text-2xl font-bold text-white">Administration DEX</h2>
          <p class="text-neutral-400 text-xs sm:text-sm">Gérez les connecteurs et les clés API</p>
        </div>
        <button
          @click="showAddDexModal = true"
          class="bg-indigo-500 hover:bg-indigo-600 text-white px-3 sm:px-4 py-2 rounded-lg sm:rounded-xl text-xs sm:text-sm font-medium transition-colors flex items-center gap-2 w-full sm:w-auto justify-center"
        >
          <span class="iconify" data-icon="lucide:plus" data-width="16"></span>
          Ajouter un DEX
        </button>
      </div>

      <!-- DEX List -->
      <div class="glass-card rounded-2xl sm:rounded-3xl overflow-hidden overflow-x-auto">
        <table class="w-full min-w-[500px]">
          <thead class="bg-white/5">
            <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Nom</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Endpoint API</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium">Status</th>
              <th class="px-4 sm:px-6 py-3 sm:py-4 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-for="dex in dexes" :key="dex.id" class="group hover:bg-white/5 transition-colors">
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <span class="text-xs sm:text-sm font-medium text-white">{{ dex.name }}</span>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4">
                <span class="text-[10px] sm:text-xs text-neutral-400 font-mono">{{ dex.apiEndpoint }}</span>
              </td>
              <td class="px-4 sm:px-6 py-3 sm:py-4">
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
              <td class="px-4 sm:px-6 py-3 sm:py-4 text-right">
                <button class="text-neutral-400 hover:text-white transition-colors">
                  <span class="iconify" data-icon="lucide:settings" data-width="16"></span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ===== ADD ADMIN MODAL ===== -->
    <div v-if="showAddAdminModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-3 sm:p-4">
      <div class="glass-card w-full max-w-sm sm:max-w-lg rounded-2xl sm:rounded-3xl p-4 sm:p-6 relative">
        <button @click="showAddAdminModal = false" class="absolute top-3 right-3 sm:top-4 sm:right-4 text-neutral-400 hover:text-white">
          <span class="iconify" data-icon="lucide:x" data-width="20"></span>
        </button>

        <div class="flex items-center gap-2 sm:gap-3 mb-4 sm:mb-6">
          <div class="w-8 h-8 sm:w-10 sm:h-10 rounded-lg sm:rounded-xl bg-purple-500/20 flex items-center justify-center">
            <span class="iconify text-purple-400" data-icon="lucide:shield-plus" data-width="20"></span>
          </div>
          <h3 class="text-lg sm:text-xl font-bold text-white">Ajouter un Administrateur</h3>
        </div>

        <form @submit.prevent="addAdmin" class="space-y-3 sm:space-y-4">
          <div class="space-y-1.5 sm:space-y-2">
            <label class="text-xs font-medium text-neutral-300">Adresse Wallet (Ethereum)</label>
            <input
              v-model="newAdminWallet"
              type="text"
              class="w-full bg-white/5 border border-white/10 rounded-lg sm:rounded-xl px-3 sm:px-4 py-2.5 sm:py-3 text-white font-mono text-xs sm:text-sm focus:outline-none focus:border-purple-500/50"
              placeholder="0x..."
              pattern="^0x[a-fA-F0-9]{40}$"
              title="Adresse Ethereum valide (0x...)"
            >
            <p class="text-[10px] sm:text-xs text-neutral-500">L'utilisateur sera créé automatiquement s'il n'existe pas</p>
          </div>

          <div class="pt-2 sm:pt-4 flex flex-col-reverse sm:flex-row justify-end gap-2 sm:gap-3">
            <button
              type="button"
              @click="showAddAdminModal = false"
              class="px-4 py-2 text-neutral-400 hover:text-white transition-colors text-xs sm:text-sm w-full sm:w-auto"
            >
              Annuler
            </button>
            <button
              type="submit"
              :disabled="isLoading || !newAdminWallet.trim()"
              class="bg-purple-500 hover:bg-purple-600 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 sm:px-6 py-2 rounded-lg sm:rounded-xl text-xs sm:text-sm font-medium transition-colors flex items-center justify-center gap-2 w-full sm:w-auto"
            >
              <span v-if="isLoading" class="iconify animate-spin" data-icon="lucide:loader-2" data-width="16"></span>
              {{ isLoading ? 'Ajout...' : 'Ajouter Admin' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- ===== ADD DEX MODAL ===== -->
    <div v-if="showAddDexModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-3 sm:p-4">
      <div class="glass-card w-full max-w-sm sm:max-w-lg rounded-2xl sm:rounded-3xl p-4 sm:p-6 relative">
        <button @click="showAddDexModal = false" class="absolute top-3 right-3 sm:top-4 sm:right-4 text-neutral-400 hover:text-white">
          <span class="iconify" data-icon="lucide:x" data-width="20"></span>
        </button>

        <h3 class="text-lg sm:text-xl font-bold text-white mb-4 sm:mb-6">Connecter un nouveau DEX</h3>

        <form @submit.prevent="addDex" class="space-y-3 sm:space-y-4">
          <div class="space-y-1.5 sm:space-y-2">
            <label class="text-xs font-medium text-neutral-300">Nom du DEX</label>
            <input v-model="newDex.name" type="text" class="w-full bg-white/5 border border-white/10 rounded-lg sm:rounded-xl px-3 sm:px-4 py-2 text-sm text-white focus:outline-none focus:border-indigo-500/50" placeholder="Ex: Binance, Bybit...">
          </div>
          <div class="space-y-1.5 sm:space-y-2">
            <label class="text-xs font-medium text-neutral-300">Endpoint API</label>
            <input v-model="newDex.apiEndpoint" type="text" class="w-full bg-white/5 border border-white/10 rounded-lg sm:rounded-xl px-3 sm:px-4 py-2 text-sm text-white focus:outline-none focus:border-indigo-500/50" placeholder="https://api.exchange.com">
          </div>
          <div class="space-y-1.5 sm:space-y-2">
            <label class="text-xs font-medium text-neutral-300">API Key</label>
            <input v-model="newDex.apiKey" type="password" class="w-full bg-white/5 border border-white/10 rounded-lg sm:rounded-xl px-3 sm:px-4 py-2 text-sm text-white focus:outline-none focus:border-indigo-500/50" placeholder="••••••••">
          </div>

          <div class="pt-2 sm:pt-4 flex flex-col-reverse sm:flex-row justify-end gap-2 sm:gap-3">
            <button type="button" @click="showAddDexModal = false" class="px-4 py-2 text-neutral-400 hover:text-white transition-colors text-xs sm:text-sm w-full sm:w-auto">Annuler</button>
            <button type="submit" class="bg-indigo-500 hover:bg-indigo-600 text-white px-4 sm:px-6 py-2 rounded-lg sm:rounded-xl text-xs sm:text-sm font-medium transition-colors w-full sm:w-auto">Ajouter</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
