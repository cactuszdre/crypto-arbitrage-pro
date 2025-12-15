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
      <div class="relative hidden lg:block pointer-events-auto">
         <!-- Abstract Glow Behind -->
         <div class="absolute inset-0 bg-gradient-to-tr from-emerald-500/10 to-cyan-500/10 blur-3xl -z-10 rounded-full"></div>

         <!-- LOADING STATE -->
         <div v-if="isLoadingData" class="relative p-6 rounded-2xl bg-gradient-to-br from-white/[0.08] to-white/[0.02] border border-white/10 h-[380px] flex items-center justify-center">
             <div class="text-emerald-500/50 flex flex-col items-center gap-4">
                 <svg class="w-10 h-10 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                 <span class="text-sm tracking-widest font-mono text-neutral-500">SCANNING MARKETS...</span>
             </div>
         </div>

         <!-- REAL DATA CARD (Carousel of Top 3 Opportunities) -->
         <div v-else-if="currentOpp" class="group relative p-5 rounded-2xl border backdrop-blur-xl transition-all duration-500 bg-gradient-to-br from-white/[0.08] to-white/[0.02] border-emerald-500/30 shadow-lg shadow-emerald-500/10 ring-1 ring-emerald-500/20">
            <!-- Glass reflection effect -->
            <div class="absolute inset-0 rounded-2xl overflow-hidden pointer-events-none">
              <div class="absolute -top-1/2 -left-1/2 w-full h-full bg-gradient-to-br from-white/10 to-transparent rotate-12 opacity-50"></div>
            </div>

            <!-- Header: Symbol + Carousel Indicators -->
            <div class="relative flex items-center justify-between gap-2 mb-4">
              <div class="flex items-center gap-3 min-w-0 flex-1">
                <!-- Icon -->
                <div class="relative shrink-0">
                  <img
                    :key="currentOpp.symbol"
                    :src="`https://app.paradex.trade/static/market/${getBaseSymbol(currentOpp.symbol).toUpperCase()}.svg`"
                    :alt="getBaseSymbol(currentOpp.symbol)"
                    class="w-12 h-12 rounded-xl border border-white/10 bg-neutral-800/50 object-cover shadow-lg transition-opacity duration-300"
                    @error="(e) => e.target.style.display = 'none'"
                  >
                </div>
                <!-- Symbol name -->
                <div class="min-w-0">
                  <h4 class="text-base font-semibold text-white truncate">
                    {{ currentOpp.symbol.replace('-USD-PERP', '').replace('-PERP', '') }}
                  </h4>
                  <p class="text-[10px] text-neutral-500">Perpetual</p>
                </div>
              </div>

              <!-- Carousel Indicators -->
              <div class="flex items-center gap-2">
                <button
                  v-for="(opp, index) in topOpportunities"
                  :key="index"
                  @click="goToSlide(index)"
                  :class="[
                    'w-2 h-2 rounded-full transition-all duration-300',
                    currentIndex === index 
                      ? 'bg-emerald-500 w-6' 
                      : 'bg-white/20 hover:bg-white/40'
                  ]"
                ></button>
              </div>
            </div>

            <!-- Route: Long -> Short -->
            <div class="relative flex items-center gap-2 mb-4 p-3 rounded-xl bg-white/[0.03] border border-white/5">
              <div class="flex-1 flex items-center gap-2 px-3 py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
                <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse shrink-0"></span>
                <span class="text-[10px] text-emerald-400 font-medium shrink-0">LONG</span>
                <span class="text-xs text-white font-semibold ml-auto truncate">{{ currentOpp.longDex }}</span>
              </div>
              <!-- Arrow -->
              <div class="flex items-center justify-center w-6 h-6 rounded-full bg-white/5 shrink-0">
                <svg class="w-3 h-3 text-neutral-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6"/>
                </svg>
              </div>
              <div class="flex-1 flex items-center gap-2 px-3 py-2 rounded-lg bg-red-500/10 border border-red-500/20">
                <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse shrink-0"></span>
                <span class="text-[10px] text-red-400 font-medium shrink-0">SHORT</span>
                <span class="text-xs text-white font-semibold ml-auto truncate">{{ currentOpp.shortDex }}</span>
              </div>
            </div>

            <!-- Rates Grid -->
            <div class="relative grid grid-cols-2 gap-3 mb-4">
              <div class="p-3 rounded-xl bg-white/[0.03] border border-white/5">
                <p class="text-[9px] text-neutral-500 uppercase tracking-wider mb-1">Long Rate</p>
                <p class="text-sm font-semibold text-emerald-400">{{ formatRate(currentOpp.longRate) }}</p>
              </div>
              <div class="p-3 rounded-xl bg-white/[0.03] border border-white/5">
                <p class="text-[9px] text-neutral-500 uppercase tracking-wider mb-1">Short Rate</p>
                <p class="text-sm font-semibold text-red-400">{{ formatRate(currentOpp.shortRate) }}</p>
              </div>
            </div>

            <!-- Footer: Daily Return -->
            <div class="relative flex items-center justify-between pt-4 border-t border-white/5">
              <span class="text-sm text-neutral-400">Daily Return</span>
              <div class="px-4 py-2 rounded-xl text-sm font-bold bg-emerald-500/15 text-emerald-400 ring-1 ring-emerald-500/30">
                +{{ (currentOpp.spread / 365).toFixed(4) }}%<span class="text-[10px] opacity-70 ml-1">/day</span>
              </div>
            </div>
         </div>

         <!-- NO DATA STATE -->
         <div v-else class="relative p-6 rounded-2xl bg-gradient-to-br from-white/[0.08] to-white/[0.02] border border-white/10 h-[380px] flex items-center justify-center">
             <div class="text-neutral-500 flex flex-col items-center gap-4 text-center">
                 <svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path></svg>
                 <span class="text-sm font-mono">No opportunities found</span>
             </div>
         </div>


      </div>

    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue';
import * as THREE from 'three';
import { arbitrageApi } from '../services/api';

const canvasContainer = ref(null);
let scene, camera, renderer;
let frameId;
let width, height;
let wavePoints;
let raycaster, mouse;
let planeMesh;

// --- Live Data State ---
const topOpportunities = ref([]);
const currentIndex = ref(0);
const isLoadingData = ref(true);
let carouselInterval = null;

// Current opportunity to display
const currentOpp = computed(() => topOpportunities.value[currentIndex.value] || null);

// Carousel navigation
const goToSlide = (index) => {
    currentIndex.value = index;
    resetCarouselTimer();
};

const nextSlide = () => {
    if (topOpportunities.value.length > 0) {
        currentIndex.value = (currentIndex.value + 1) % topOpportunities.value.length;
    }
};

const startCarousel = () => {
    if (carouselInterval) clearInterval(carouselInterval);
    carouselInterval = setInterval(nextSlide, 3000); // Change every 3 seconds
};

const resetCarouselTimer = () => {
    if (carouselInterval) clearInterval(carouselInterval);
    startCarousel();
};

// --- Helpers ---
const formatRate = (rate) => {
    if (rate === undefined || rate === null) return '0.00%';
    return (rate > 0 ? '+' : '') + rate.toFixed(2) + '%';
};

const getBaseSymbol = (symbol) => {
    if (!symbol) return 'BTC';
    return symbol.split('-')[0].replace('PERP', '').replace('USD', '');
};

const calculateTopOpportunities = (rawRates) => {
    if (!rawRates || rawRates.length === 0) return [];

    const opportunities = [];

    for (const item of rawRates) {
        if (!item.rates || item.rates.length < 2) continue;

        const sorted = [...item.rates].sort((a, b) => parseFloat(a.rateAnnual) - parseFloat(b.rateAnnual));
        const bestLong = sorted[0];
        const bestShort = sorted[sorted.length - 1];

        if (bestLong.dex === bestShort.dex) continue;

        const spread = parseFloat(bestShort.rateAnnual) - parseFloat(bestLong.rateAnnual);
        
        if (spread > 0) {
            opportunities.push({
                symbol: item.symbol,
                longDex: bestLong.dex,
                shortDex: bestShort.dex,
                longRate: parseFloat(bestLong.rateAnnual),
                shortRate: parseFloat(bestShort.rateAnnual),
                spread: spread
            });
        }
    }

    // Sort by spread descending and return top 3
    return opportunities.sort((a, b) => b.spread - a.spread).slice(0, 3);
};

const fetchData = async () => {
    try {
        isLoadingData.value = true;
        const response = await arbitrageApi.getAllRates();
        const found = calculateTopOpportunities(response.data);
        if (found.length > 0) {
            topOpportunities.value = found;
            startCarousel();
        }
    } catch (e) {
        console.error("Hero Data Fetch Error", e);
    } finally {
        isLoadingData.value = false;
    }
};


// --- Text Scramble Effect ---
const phrases = [
    "Find the best arbitrage opportunities",
    "Learn more about crypto market efficiency",
    "Capture yield from funding rates",
    "Automate institutional-grade execution"
];
const displayedText = ref("");
let phraseIndex = 0;
let charIndex = 0;
let isDeleting = false;
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
            // Wait before switching to next phrase
            setTimeout(() => {
                nextPhrase();
            }, 3000); // Read time
        }
        
        iterations += 1; // Faster reveal
    }, 30); // Faster interval
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
    // Distance from vertex to mouse position (in world XY plane)
    float d = distance(val.xy, uMouse.xy);
    
    // Interaction radius (increase for larger effect)
    float radius = 25.0;
    float interact = smoothstep(radius, 0.0, d);
    
    // Effect: Create a turbulent spike/wave near mouse
    // Much lighter effect: reduced amplitude from 4.0 to 1.5
    float mouseWave = sin(d * 0.5 - uTime * 5.0) * 1.5 * interact;
    
    h += mouseWave;

    // Apply height
    val.z = h;
    vHeight = h;

    gl_Position = projectionMatrix * modelViewMatrix * vec4(val, 1.0);
    
    // Dynamic point size: bigger near crests/mouse
    // Reduced scaling from +2.0 to +1.0
    gl_PointSize = 2.5 + (interact * 1.0); 
  }
`;

/* Fragment Shader: Emerald/Cyan Gradient based on Height with Radial Fade */
const fragmentShader = `
  varying float vHeight;
  varying vec2 vUv;

  void main() {
    // Colors
    vec3 colorLow = vec3(0.02, 0.2, 0.15); // Dark Green/Gray
    vec3 colorHigh = vec3(0.2, 0.9, 0.6); // Emerald bright

    // Mix based on height
    float mixFactor = smoothstep(-5.0, 5.0, vHeight);
    
    // Circular particle shape
    vec2 cxy = 2.0 * gl_PointCoord - 1.0;
    float r = dot(cxy, cxy);
    if (r > 1.0) discard;
    
    float alpha = 1.0 - smoothstep(0.5, 1.0, r);

    // Radial Fade Mask
    float distFromCenter = distance(vUv, vec2(0.5));
    float edgeMask = 1.0 - smoothstep(0.35, 0.5, distFromCenter);

    vec3 finalColor = mix(colorLow, colorHigh, mixFactor);
    gl_FragColor = vec4(finalColor, alpha * 0.7 * edgeMask); 
  }
`;

const initThree = () => {
    width = window.innerWidth;
    height = window.innerHeight;
    raycaster = new THREE.Raycaster();
    mouse = new THREE.Vector2(9999, 9999); // Start off-screen

    scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x050505, 0.02);

    camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 100);
    camera.position.set(0, -20, 30);
    camera.lookAt(0, 0, 0);

    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    canvasContainer.value.appendChild(renderer.domElement);

    // Geometry: Grid of Points
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
    scene.add(wavePoints);

    // Invisible Plane for Raycasting (matches Points plane Z=0)
    const planeGeo = new THREE.PlaneGeometry(500, 500);
    const planeMat = new THREE.MeshBasicMaterial({ visible: false });
    planeMesh = new THREE.Mesh(planeGeo, planeMat);
    scene.add(planeMesh);

    animate();
    window.addEventListener('resize', onResize);
    window.addEventListener('mousemove', onMouseMove);
};

const animate = () => {
    frameId = requestAnimationFrame(animate);
    const time = performance.now() * 0.001;
    
    if (wavePoints) {
        wavePoints.material.uniforms.uTime.value = time;
        wavePoints.rotation.z = time * 0.05;
        
        // Raycast
        raycaster.setFromCamera(mouse, camera);
        const intersects = raycaster.intersectObject(planeMesh);
        
        if (intersects.length > 0) {
            // Transform intersection point back to local space of the rotating points?
            // wavePoints rotates around Z. 
            // So world point P -> Inverse Rotate -> Local Point.
            
            const hit = intersects[0].point;
            
            // Apply inverse rotation of the container to match the grid
            const angle = -wavePoints.rotation.z;
            const x = hit.x * Math.cos(angle) - hit.y * Math.sin(angle);
            const y = hit.x * Math.sin(angle) + hit.y * Math.cos(angle);
            
            wavePoints.material.uniforms.uMouse.value.set(x, y, 0);
        } else {
             // Reset if off plane (rare due to big plane)
             // wavePoints.material.uniforms.uMouse.value.set(9999, 9999, 0);
        }
    }

    renderer.render(scene, camera);
};

const onMouseMove = (event) => {
    // Normalized Device Coords
    mouse.x = (event.clientX / width) * 2 - 1;
    mouse.y = -(event.clientY / height) * 2 + 1;
};

const onResize = () => {
    width = window.innerWidth;
    height = window.innerHeight;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
};

onMounted(() => {
    initThree();
    startTextAnimation();
    fetchData();
});

onUnmounted(() => {
    cancelAnimationFrame(frameId);
    window.removeEventListener('resize', onResize);
    window.removeEventListener('mousemove', onMouseMove);
    if(renderer) renderer.dispose();
    if(scrambleInterval) clearInterval(scrambleInterval);
    if(carouselInterval) clearInterval(carouselInterval);
});
</script>

<style scoped>
.animate-float-slow {
    animation: float 6s ease-in-out infinite;
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-20px); }
}
</style>
