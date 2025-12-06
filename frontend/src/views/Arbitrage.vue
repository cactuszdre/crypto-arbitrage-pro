<script setup>
import { ref, onMounted } from 'vue'
import { arbitrageApi } from '../services/api'

const opportunities = ref([])

onMounted(async () => {
    try {
        const response = await arbitrageApi.getOpportunities()
        opportunities.value = response.data
    } catch (error) {
        console.error('Failed to fetch opportunities:', error)
    }
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
    
    <!-- Opportunities List -->
    <div class="lg:col-span-2 space-y-6">
      <div class="glass-card rounded-3xl overflow-hidden">
        <div class="px-6 py-5 border-b border-white/5 flex items-center justify-between">
          <h3 class="text-base font-medium text-white">Live Opportunities</h3>
          <span class="text-xs text-indigo-400">{{ opportunities.length }} found</span>
        </div>
        
        <!-- Empty State -->
        <div v-if="opportunities.length === 0" class="p-12 text-center">
          <span class="text-neutral-600 mb-4 text-5xl">📭</span>
          <p class="text-neutral-500 text-sm">Pas assez de données pour le moment</p>
          <p class="text-neutral-600 text-xs mt-1">Attendez le prochain cycle de fetch ou ajoutez plus de DEX</p>
        </div>

        <table v-else class="w-full">
          <thead class="bg-white/5">
            <tr class="text-left text-[10px] uppercase tracking-wider text-neutral-500">
              <th class="px-6 py-4 font-medium">Pair</th>
              <th class="px-6 py-4 font-medium">Route</th>
              <th class="px-6 py-4 font-medium">Spread (Annual)</th>
              <th class="px-6 py-4 font-medium">Daily Return</th>
              <th class="px-6 py-4 font-medium text-right"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr 
              v-for="opp in opportunities" 
              :key="opp.symbol" 
              class="group hover:bg-white/5 transition-colors"
            >
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <div class="flex -space-x-2">
                    <img 
                      src="https://cryptologos.cc/logos/bitcoin-btc-logo.png?v=025" 
                      class="w-6 h-6 rounded-full border border-black grayscale group-hover:grayscale-0 transition-all"
                      v-if="opp.symbol.startsWith('BTC')"
                    >
                    <img 
                      src="https://cryptologos.cc/logos/ethereum-eth-logo.png?v=025" 
                      class="w-6 h-6 rounded-full border border-black grayscale group-hover:grayscale-0 transition-all"
                      v-else-if="opp.symbol.startsWith('ETH')"
                    >
                    <img 
                      src="https://cryptologos.cc/logos/solana-sol-logo.png?v=025" 
                      class="w-6 h-6 rounded-full border border-black grayscale group-hover:grayscale-0 transition-all"
                      v-else-if="opp.symbol.startsWith('SOL')"
                    >
                    <div 
                      v-else-if="opp.symbol.startsWith('RESOLV')"
                      class="w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center text-[8px] text-white font-bold border border-black"
                    >R</div>
                     <div v-else class="w-6 h-6 rounded-full bg-neutral-800 flex items-center justify-center text-[8px] text-white">?</div>
                  </div>
                  <span class="text-xs font-medium text-neutral-300 group-hover:text-white">
                    {{ opp.symbol.replace('-USD-PERP', '') }}
                  </span>
                </div>
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center gap-2 text-xs text-neutral-400">
                  <span class="text-emerald-400">Long {{ opp.longDex }}</span>
                  <span>→</span>
                  <span class="text-red-400">Short {{ opp.shortDex }}</span>
                </div>
              </td>
              <td class="px-6 py-4">
                <span class="text-xs font-medium text-white">
                  {{ (opp.spread).toFixed(2) }}%
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="text-xs font-medium text-emerald-400">
                  +{{ (opp.projectedDailyReturn).toFixed(4) }}%
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <button class="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center text-neutral-400 hover:text-white hover:bg-white/10 transition-colors">
                  ▶️
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Strategy Explanation -->
      <div class="glass-card rounded-3xl p-6">
        <h3 class="text-base font-medium text-white mb-4">Funding Rate Arbitrage Strategy</h3>
        <div class="space-y-4 text-sm text-neutral-400">
          <p>
            <span class="text-white font-medium">Principe:</span> Profiter des différences de funding rates entre DEX.
          </p>
          <div class="p-4 bg-white/5 rounded-xl border border-white/5">
            <p class="text-xs font-mono">
              <span class="text-emerald-400">LONG</span> sur le DEX avec le funding le plus négatif<br>
              <span class="text-red-400">SHORT</span> sur l'autre DEX<br>
              <span class="text-indigo-400">PROFIT</span> = |rate1| + |rate2| (si les deux sont négatifs)
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Strategy Config Panel -->
    <div class="glass-card rounded-3xl p-6 flex flex-col justify-between">
      <div>
        <h3 class="text-base font-medium text-white mb-6">Execution Strategy</h3>
        
        <div class="space-y-8">
          <!-- Slider 1 -->
          <div class="group">
            <div class="flex justify-between mb-3">
              <span class="text-xs text-neutral-400 font-medium group-hover:text-white transition-colors">Risk Tolerance</span>
              <span class="text-xs text-white">Medium</span>
            </div>
            <input type="range" class="w-full" value="45">
          </div>

          <!-- Slider 2 -->
          <div class="group">
            <div class="flex justify-between mb-3">
              <span class="text-xs text-neutral-400 font-medium group-hover:text-white transition-colors">Profit Trigger</span>
              <span class="text-xs text-white">0.5%</span>
            </div>
            <input type="range" class="w-full" value="20">
          </div>

          <!-- Switch -->
          <div class="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center">
                ⚡
              </div>
              <div class="flex flex-col">
                <span class="text-xs font-medium text-white">Auto-Pilot</span>
                <span class="text-[10px] text-neutral-500">Execute under 50ms</span>
              </div>
            </div>
            <div class="w-10 h-6 bg-indigo-600 rounded-full relative cursor-pointer">
              <div class="absolute top-1 right-1 w-4 h-4 bg-white rounded-full shadow-sm"></div>
            </div>
          </div>
        </div>
      </div>
      
      <button class="mt-8 w-full py-3 bg-white text-black text-xs font-semibold rounded-xl hover:bg-neutral-200 transition-colors shadow-lg shadow-white/10 cursor-pointer">
        Calibrate Strategy
      </button>
    </div>
  </div>
</template>
