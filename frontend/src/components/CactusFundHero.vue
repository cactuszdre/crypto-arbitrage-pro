<template>
  <div class="relative min-h-screen bg-[#050505] text-white overflow-hidden flex items-center justify-center">
    
    <!-- Background: Three.js Animation -->
    <div ref="canvasContainer" class="absolute inset-0 z-0 opacity-60"></div>

    <!-- Foreground Content -->
    <div class="relative z-10 w-full max-w-7xl mx-auto px-6 lg:px-8 grid lg:grid-cols-2 gap-16 items-center pointer-events-none">
      
      <!-- Left Column: Value Proposition -->
      <div class="flex flex-col gap-8 text-center lg:text-left pointer-events-auto">
        <!-- Badge -->
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/50 border border-emerald-500/20 w-fit mx-auto lg:mx-0 backdrop-blur-sm">
            <span class="relative flex h-2 w-2">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            <span class="text-xs font-medium text-emerald-400 font-mono tracking-wide">SYSTEM OPERATIONAL</span>
        </div>

        <!-- Headline -->
        <h1 class="text-6xl lg:text-8xl font-bold tracking-tight leading-[1.1]">
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-cyan-400">
            CactusFund
          </span>
        </h1>

        <!-- Dynamic Subline (Hacker Effect) -->
        <div class="h-16 flex items-center justify-center lg:justify-start">
             <p class="text-lg text-gray-400 max-w-xl font-mono leading-relaxed h-full flex items-center">
                > {{ displayedText }}<span class="animate-pulse">_</span>
             </p>
        </div>

        <!-- CTA Buttons -->
        <div class="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
            <button @click="$router.push('/arbitrage')" class="px-8 py-3.5 bg-emerald-500 hover:bg-emerald-400 text-black font-semibold rounded-lg transition-all shadow-[0_0_20px_rgba(16,185,129,0.3)] hover:shadow-[0_0_30px_rgba(16,185,129,0.5)] flex items-center justify-center gap-2 group">
                Launch Opportunities
                <svg class="w-4 h-4 group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
            </button>
            <button @click="$router.push('/learn')" class="px-8 py-3.5 bg-white/5 hover:bg-white/10 text-white font-medium rounded-lg border border-white/10 transition-all backdrop-blur-sm">
                View Documentation
            </button>
        </div>
        
        <!-- Trust/Stats Row -->
        <div class="pt-8 border-t border-white/5 flex flex-wrap justify-center lg:justify-start gap-8 lg:gap-12">
            <div>
                <div class="text-2xl font-bold text-white">4</div>
                <div class="text-xs text-gray-500 uppercase tracking-widest mt-1">DEXs Connected</div>
            </div>
            <div>
                <div class="text-2xl font-bold text-white">100+</div>
                <div class="text-xs text-gray-500 uppercase tracking-widest mt-1">Arbitrage Pairs</div>
            </div>
        </div>
      </div>

      <!-- Right Column: Live Data Card -->
      <!-- Right Column: Carousel Data Card -->
      <div class="relative hidden lg:block pointer-events-auto h-[450px] overflow-hidden">
          <!-- Glow Behind -->
          <div class="absolute inset-0 bg-gradient-to-tr from-emerald-500/10 to-cyan-500/10 blur-3xl -z-10 rounded-full"></div>

          <!-- Loading State -->
          <div v-if="isLoadingData" class="relative p-6 rounded-2xl bg-gradient-to-br from-white/[0.08] to-white/[0.02] border border-white/10 h-full flex items-center justify-center">
             <div class="text-emerald-500/50 flex flex-col items-center gap-4">
                 <svg class="w-10 h-10 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                 <span class="text-sm tracking-widest font-mono text-neutral-500">SCANNING MARKETS...</span>
             </div>
          </div>

          <!-- Carousel Card (Minimal & Elegant) -->
          <Transition name="slide-blur">
              <div v-if="!isLoadingData && currentOpp" :key="currentOpp.symbol" class="h-full w-full relative group perspective-1000">
                  
                  <div class="relative h-full flex flex-col p-8 rounded-2xl border border-white/5 bg-[#050505] shadow-2xl transition-all duration-500 hover:border-white/10 overflow-hidden">
                      
                      <!-- Header: Asset & Rank -->
                      <div class="flex items-center justify-between mb-8">
                          <div class="flex items-center gap-4">
                              <img 
                                  :src="`https://app.paradex.trade/static/market/${getBaseSymbol(currentOpp.symbol).toUpperCase()}.svg`" 
                                  class="w-12 h-12 rounded-full border border-neutral-800 bg-neutral-900"
                                  @error="(e) => e.target.style.display = 'none'"
                              >
                              <div>
                                  <h3 class="text-xl font-medium text-white tracking-tight">{{ getBaseSymbol(currentOpp.symbol) }}</h3>
                                  <p class="text-xs text-neutral-500 font-mono">PERPETUAL FUTURE</p>
                              </div>
                          </div>
                          <div class="px-3 py-1 rounded-full border border-white/5 bg-white/[0.02] text-xs font-medium text-neutral-400">
                              #{{ currentIndex + 1 }} Top Opportunity
                          </div>
                      </div>

                      <!-- Main Content: The Strategy Flow -->
                      <div class="flex-1 relative flex flex-col justify-center pl-2">
                          <!-- Vertical Connector Line -->
                          <div class="absolute left-9 top-6 bottom-6 w-px bg-gradient-to-b from-emerald-500/20 via-neutral-800 to-red-500/20"></div>

                          <!-- Leg 1: Long -->
                          <div class="relative flex items-center gap-5 mb-8 group/item">
                               <div class="relative z-10 w-14 h-14 rounded-full bg-[#0A0A0A] border border-emerald-500/20 flex items-center justify-center shrink-0 group-hover/item:border-emerald-500/40 transition-colors">
                                  <div class="text-[10px] font-bold text-emerald-500 uppercase">Buy</div>
                               </div>
                               <div>
                                   <div class="text-[10px] uppercase text-neutral-500 tracking-wider font-medium mb-0.5">Step 1 • Open Long</div>
                                   <div class="text-base text-white">
                                      <span class="font-medium">{{ currentOpp.longDex }}</span>
                                      <span class="text-neutral-600 mx-2">|</span> 
                                      <span class="text-neutral-400 text-sm">Rate: <span class="text-emerald-400/80">{{ formatRate(currentOpp.longRate) }}</span></span>
                                   </div>
                               </div>
                          </div>

                          <!-- Leg 2: Short -->
                          <div class="relative flex items-center gap-5 group/item">
                               <div class="relative z-10 w-14 h-14 rounded-full bg-[#0A0A0A] border border-red-500/20 flex items-center justify-center shrink-0 group-hover/item:border-red-500/40 transition-colors">
                                  <div class="text-[10px] font-bold text-red-500 uppercase">Sell</div>
                               </div>
                               <div>
                                   <div class="text-[10px] uppercase text-neutral-500 tracking-wider font-medium mb-0.5">Step 2 • Open Short</div>
                                   <div class="text-base text-white">
                                      <span class="font-medium">{{ currentOpp.shortDex }}</span>
                                      <span class="text-neutral-600 mx-2">|</span> 
                                      <span class="text-neutral-400 text-sm">Rate: <span class="text-red-400/80">{{ formatRate(currentOpp.shortRate) }}</span></span>
                                   </div>
                               </div>
                          </div>
                      </div>

                      <!-- Footer: Net Result -->
                      <div class="mt-8 pt-6 border-t border-white/5 flex items-end justify-between">
                          <div>
                              <p class="text-[10px] uppercase text-neutral-500 tracking-widest font-semibold mb-1">Annual Return</p>
                              <div class="text-3xl font-light text-white tracking-tighter">
                                  {{ formatRate(currentOpp.spread) }}
                              </div>
                          </div>
                      </div>

                       <!-- Minimal Progress Bar -->
                       <div class="absolute bottom-0 left-0 w-full h-[2px] bg-neutral-900">
                           <div class="h-full bg-white opacity-20 animate-[width_5s_linear_infinite]" :key="currentIndex"></div>
                       </div>
                  </div>
              </div>
          </Transition>

          <!-- No Data State key fallback -->
          <div v-if="!isLoadingData && !currentOpp" class="h-full flex items-center justify-center border border-white/10 rounded-2xl bg-white/[0.02]">
              <span class="text-neutral-500 font-mono">No active opportunities</span>
          </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue';
import * as THREE from 'three';
import { arbitrageApi } from '../services/api';

// --- Background Animation Refs (Keep existing background) ---
const canvasContainer = ref(null);
let bgScene, bgCamera, bgRenderer;
let bgFrameId;
let width, height;
let wavePoints;
let raycaster, mouse;
let planeMesh;

// --- Carousel State ---
const topOpportunities = ref([]);
const currentIndex = ref(0);
const isLoadingData = ref(true);
const carouselInterval = ref(null);

const currentOpp = computed(() => {
    if (topOpportunities.value.length === 0) return null;
    return topOpportunities.value[currentIndex.value];
});

// --- Helpers ---
const formatRate = (rate) => {
    if (rate === undefined || rate === null) return '0.00%';
    return (rate > 0 ? '+' : '') + rate.toFixed(2) + '%';
};

const getBaseSymbol = (symbol) => {
    if (!symbol) return 'BTC';
    return symbol.split('-')[0].replace('PERP', '').replace('USD', '');
};

// --- Data Logic ---
const fetchData = async () => {
    try {
        isLoadingData.value = true;
        const response = await arbitrageApi.getAllRates();
        processTopOpportunities(response.data);
    } catch (e) {
        console.error("Data Fetch Error", e);
    } finally {
        isLoadingData.value = false;
    }
};

const processTopOpportunities = (data) => {
    let allArbs = [];

    for (const item of data) {
        if (!item.rates || item.rates.length < 2) continue;

        // Sort rates ascending (Lowest Long, Highest Short ideally)
        const sorted = [...item.rates].sort((a, b) => parseFloat(a.rateAnnual) - parseFloat(b.rateAnnual));
        const bestLong = sorted[0];
        const bestShort = sorted[sorted.length - 1];

        if (bestLong.dex === bestShort.dex) continue;

        // Arbitrage Spread = Short Rate - Long Rate
        const spread = parseFloat(bestShort.rateAnnual) - parseFloat(bestLong.rateAnnual);
        
        if (spread > 0) {
            allArbs.push({
                symbol: item.symbol,
                longDex: bestLong.dex,
                shortDex: bestShort.dex,
                longRate: parseFloat(bestLong.rateAnnual),
                shortRate: parseFloat(bestShort.rateAnnual),
                spread: spread
            });
        }
    }
    
    // Sort by best spread first
    allArbs.sort((a, b) => b.spread - a.spread);
    
    // Filter for unique symbols (Diversity) - Top 3 Unique
    const uniqueSyms = new Set();
    const finalOps = [];
    for(const arb of allArbs) {
        const base = getBaseSymbol(arb.symbol);
        if(!uniqueSyms.has(base)) {
             uniqueSyms.add(base);
             finalOps.push(arb);
        }
        if(finalOps.length >= 3) break;
    }
    
    topOpportunities.value = finalOps;

    if (finalOps.length > 1) {
        startCarousel();
    }
};

const startCarousel = () => {
    if (carouselInterval.value) clearInterval(carouselInterval.value);
    carouselInterval.value = setInterval(() => {
        currentIndex.value = (currentIndex.value + 1) % topOpportunities.value.length;
    }, 5000); 
};

// --- Text Scramble Effect (Keep existing) ---
const phrases = [
    "Find the best arbitrage opportunities",
    "Learn more about crypto market efficiency",
    "Capture yield from funding rates",
    "Automate institutional-grade execution"
];
const displayedText = ref("");
let phraseIndex = 0;
let scrambleInterval = null;
const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()";

const startTextAnimation = () => {
    const targetPhrase = phrases[phraseIndex];
    let iterations = 0;
    
    if (scrambleInterval) clearInterval(scrambleInterval);

    scrambleInterval = setInterval(() => {
        displayedText.value = targetPhrase
            .split("")
            .map((char, index) => {
                if (index < iterations) {
                    return targetPhrase[index];
                }
                return chars[Math.floor(Math.random() * chars.length)];
            })
            .join("");

        if (iterations >= targetPhrase.length) { 
            clearInterval(scrambleInterval);
            setTimeout(() => {
                nextPhrase();
            }, 3000); 
        }
        
        iterations += 1; 
    }, 30); 
};

const nextPhrase = () => {
    phraseIndex = (phraseIndex + 1) % phrases.length;
    startTextAnimation();
};

/* Vertex Shader: Sine Wave Displacement + Mouse Interaction */
const vertexShader = `
  uniform float uTime;
  uniform vec3 uMouse;
  varying vec2 vUv;
  varying float vHeight;

  void main() {
    vUv = uv;
    
    vec3 val = position;
    
    // Base ambient waves
    float h = sin(val.x * 0.1 + uTime * 1.0) * 2.0 
            + sin(val.y * 0.1 + uTime * 1.2) * 2.0;

    h += sin(val.x * 0.3 + uTime * 0.5) * 0.5;

    // --- Mouse Interaction ---
    float d = distance(val.xy, uMouse.xy);
    float radius = 25.0;
    float interact = smoothstep(radius, 0.0, d);
    
    float mouseWave = sin(d * 0.5 - uTime * 5.0) * 1.5 * interact;
    
    h += mouseWave;

    val.z = h;
    vHeight = h;

    gl_Position = projectionMatrix * modelViewMatrix * vec4(val, 1.0);
    
    gl_PointSize = 2.5 + (interact * 1.0); 
  }
`;

/* Fragment Shader */
const fragmentShader = `
  varying float vHeight;
  varying vec2 vUv;

  void main() {
    vec3 colorLow = vec3(0.02, 0.2, 0.15); 
    vec3 colorHigh = vec3(0.2, 0.9, 0.6); 

    float mixFactor = smoothstep(-5.0, 5.0, vHeight);
    
    vec2 cxy = 2.0 * gl_PointCoord - 1.0;
    float r = dot(cxy, cxy);
    if (r > 1.0) discard;
    
    float alpha = 1.0 - smoothstep(0.5, 1.0, r);

    float distFromCenter = distance(vUv, vec2(0.5));
    float edgeMask = 1.0 - smoothstep(0.35, 0.5, distFromCenter);

    vec3 finalColor = mix(colorLow, colorHigh, mixFactor);
    gl_FragColor = vec4(finalColor, alpha * 0.7 * edgeMask); 
  }
`;

// --- Background Initialization ---
const initBackgroundThree = () => {
    width = window.innerWidth;
    height = window.innerHeight;
    raycaster = new THREE.Raycaster();
    mouse = new THREE.Vector2(9999, 9999);

    bgScene = new THREE.Scene();
    bgScene.fog = new THREE.FogExp2(0x050505, 0.02);

    bgCamera = new THREE.PerspectiveCamera(75, width / height, 0.1, 100);
    bgCamera.position.set(0, -20, 30);
    bgCamera.lookAt(0, 0, 0);

    bgRenderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    bgRenderer.setSize(width, height);
    bgRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    if (canvasContainer.value) canvasContainer.value.appendChild(bgRenderer.domElement);

    const geometry = new THREE.PlaneGeometry(180, 120, 120, 80); 
    const material = new THREE.ShaderMaterial({
        vertexShader,
        fragmentShader,
        uniforms: {
            uTime: { value: 0 },
            uMouse: { value: new THREE.Vector3(9999, 9999, 0) }
        },
        transparent: true,
        depthWrite: false
    });

    wavePoints = new THREE.Points(geometry, material);
    bgScene.add(wavePoints);

    const planeGeo = new THREE.PlaneGeometry(500, 500);
    const planeMat = new THREE.MeshBasicMaterial({ visible: false });
    planeMesh = new THREE.Mesh(planeGeo, planeMat);
    bgScene.add(planeMesh);
};

const animate = () => {
    bgFrameId = requestAnimationFrame(animate);
    const time = performance.now() * 0.001;
    
    // Background Update
    if (wavePoints) {
        wavePoints.material.uniforms.uTime.value = time;
        wavePoints.rotation.z = time * 0.05;
        
        raycaster.setFromCamera(mouse, bgCamera);
        const intersects = raycaster.intersectObject(planeMesh);
        
        if (intersects.length > 0) {
            const hit = intersects[0].point;
            const angle = -wavePoints.rotation.z;
            const x = hit.x * Math.cos(angle) - hit.y * Math.sin(angle);
            const y = hit.x * Math.sin(angle) + hit.y * Math.cos(angle);
            wavePoints.material.uniforms.uMouse.value.set(x, y, 0);
        }
        bgRenderer.render(bgScene, bgCamera);
    }
};

const onMouseMove = (event) => {
    mouse.x = (event.clientX / width) * 2 - 1;
    mouse.y = -(event.clientY / height) * 2 + 1;
};

const onResize = () => {
    width = window.innerWidth;
    height = window.innerHeight;
    
    // Bg Resize
    bgCamera.aspect = width / height;
    bgCamera.updateProjectionMatrix();
    bgRenderer.setSize(width, height);
};

onMounted(() => {
    initBackgroundThree();
    startTextAnimation();
    animate(); // Crucial: Start the animation loop
    fetchData(); 
});

onUnmounted(() => {
    cancelAnimationFrame(bgFrameId);
    window.removeEventListener('resize', onResize);
    window.removeEventListener('mousemove', onMouseMove);
    if(bgRenderer) bgRenderer.dispose();
    if(scrambleInterval) clearInterval(scrambleInterval);
    if(carouselInterval.value) clearInterval(carouselInterval.value);
});
</script>

<style scoped>
.slide-blur-enter-active,
.slide-blur-leave-active {
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.slide-blur-enter-from {
  opacity: 0;
  transform: translateX(30%);
  filter: blur(12px);
}

.slide-blur-leave-to {
  opacity: 0;
  transform: translateX(-30%);
  filter: blur(12px);
}

.animate-float-slow {
    animation: float 6s ease-in-out infinite;
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-20px); }
}
</style>
