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
      <!-- Right Column: Visual Animation -->
      <div class="relative hidden lg:block pointer-events-auto h-[500px] w-full flex items-center justify-center perspective-1000">
         
         <!-- Orbital Canvas Container -->
         <div ref="cardCanvasContainer" class="absolute inset-0 pointer-events-none z-0"></div>

         <!-- Labels Overlay (controlled by Three.js) -->
         <div class="absolute inset-0 pointer-events-none z-10 overflow-hidden">
             
             <div id="label-btc" class="hidden absolute top-0 left-0 px-3 py-1.5 rounded-xl bg-gradient-to-r from-orange-500/20 to-amber-500/20 border border-orange-500/40 backdrop-blur-md text-orange-300 text-xs font-bold tracking-widest shadow-[0_0_20px_rgba(249,115,22,0.2)]">
                <span class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-orange-500 animate-pulse"></span> BTC
                </span>
             </div>

             <div id="label-eth" class="hidden absolute top-0 left-0 px-3 py-1.5 rounded-xl bg-gradient-to-r from-indigo-500/20 to-purple-500/20 border border-indigo-500/40 backdrop-blur-md text-indigo-300 text-xs font-bold tracking-widest shadow-[0_0_20px_rgba(99,102,241,0.2)]">
                <span class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-indigo-500 animate-pulse"></span> ETH
                </span>
             </div>

             <div id="label-sol" class="hidden absolute top-0 left-0 px-3 py-1.5 rounded-xl bg-gradient-to-r from-emerald-500/20 to-teal-500/20 border border-emerald-500/40 backdrop-blur-md text-emerald-300 text-xs font-bold tracking-widest shadow-[0_0_20px_rgba(16,185,129,0.2)]">
                <span class="flex items-center gap-2">
                     <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span> SOL
                </span>
             </div>

             <div id="label-arb" class="hidden absolute top-0 left-0 px-3 py-1.5 rounded-xl bg-gradient-to-r from-sky-500/20 to-cyan-500/20 border border-sky-500/40 backdrop-blur-md text-sky-300 text-xs font-bold tracking-widest shadow-[0_0_20px_rgba(14,165,233,0.2)]">
                <span class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-sky-500 animate-pulse"></span> ARB
                </span>
             </div>
         </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import * as THREE from 'three';

// --- Background Animation Refs ---
const canvasContainer = ref(null);
let bgScene, bgCamera, bgRenderer;
let bgFrameId;
let width, height;
let wavePoints;
let raycaster, mouse;
let planeMesh;

// --- Card Animation Refs (3D Orbit) ---
const cardCanvasContainer = ref(null);
const cryptoLabels = ref([]); // DOM elements for labels
let cardScene, cardCamera, cardRenderer;
const cardWidth = ref(0);
const cardHeight = ref(0);
let orbitGroup;
const orbitNodes = []; // { mesh, labelId, angle, speed, radius, axis, color }

// --- Helpers ---
// (Removed data helpers)

// --- Text Scramble Effect ---
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

// --- Card Animation (Orbital) Initialization ---
const initCardThree = () => {
    if (!cardCanvasContainer.value) return;
    
    const container = cardCanvasContainer.value;
    const w = container.clientWidth;
    const h = container.clientHeight;
    cardWidth.value = w;
    cardHeight.value = h;

    cardScene = new THREE.Scene();
    
    // Moved Camera back to 24 to fit everything comfortably
    cardCamera = new THREE.PerspectiveCamera(50, w / h, 0.1, 100);
    cardCamera.position.set(0, 0, 24);
    cardCamera.lookAt(0, 0, 0);

    cardRenderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, preserveDrawingBuffer: true });
    cardRenderer.setSize(w, h);
    cardRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(cardRenderer.domElement);

    // Light
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.5);
    cardScene.add(ambientLight);
    const pointLight = new THREE.PointLight(0x10b981, 2, 50);
    pointLight.position.set(0, 0, 0);
    cardScene.add(pointLight);

    orbitGroup = new THREE.Group();
    cardScene.add(orbitGroup);

    // Central Core (CactusFund)
    const coreGeo = new THREE.IcosahedronGeometry(1.8, 1);
    const coreMat = new THREE.MeshBasicMaterial({ 
        color: 0x10b981, 
        wireframe: true, 
        transparent: true, 
        opacity: 0.6 
    });
    const coreMesh = new THREE.Mesh(coreGeo, coreMat);
    orbitGroup.add(coreMesh);
    
    // Glowing Core Center
    const innerGeo = new THREE.IcosahedronGeometry(1.0, 0);
    const innerMat = new THREE.MeshBasicMaterial({ color: 0xccfbf1 });
    const innerMesh = new THREE.Mesh(innerGeo, innerMat);
    orbitGroup.add(innerMesh);

    // Helper for Basis
    const getPerpendicularBasis = (axis) => {
        const ax = axis.clone().normalize();
        let tangent = new THREE.Vector3(0, 1, 0);
        if (Math.abs(ax.y) > 0.99) tangent.set(1, 0, 0);
        const u = new THREE.Vector3().crossVectors(ax, tangent).normalize();
        const v = new THREE.Vector3().crossVectors(ax, u).normalize();
        return { u, v };
    };

    // Nodes Data
    const nodeData = [
        { id: 'btc', label: 'BTC', radius: 5, speed: 0.8, color: 0xf7931a, img: 'https://s2.coinmarketcap.com/static/img/coins/64x64/1.png', axis: new THREE.Vector3(0, 1, 0.2).normalize() },
        { id: 'eth', label: 'ETH', radius: 7, speed: 0.5, color: 0x627eea, img: 'https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png', axis: new THREE.Vector3(1, 0.5, 0).normalize() },
        { id: 'sol', label: 'SOL', radius: 6, speed: 0.6, color: 0x14f195, img: 'https://s2.coinmarketcap.com/static/img/coins/64x64/5426.png', axis: new THREE.Vector3(-0.5, 1, 0).normalize() },
        { id: 'arb', label: 'ARB', radius: 8.5, speed: 0.3, color: 0x28a0f0, img: 'https://s2.coinmarketcap.com/static/img/coins/64x64/11841.png', axis: new THREE.Vector3(0.5, -0.5, 1).normalize() },
    ];

    const textureLoader = new THREE.TextureLoader();

    nodeData.forEach((d) => {
        // Calculate Basis for this Node's Plane
        const { u, v } = getPerpendicularBasis(d.axis);

        // 1. Create Orbit Path (Visual Ring)
        const points = [];
        const segments = 64;
        for (let i = 0; i <= segments; i++) {
            const theta = (i / segments) * Math.PI * 2;
            const x = Math.cos(theta) * d.radius;
            const y = Math.sin(theta) * d.radius;
            const p = u.clone().multiplyScalar(x).add(v.clone().multiplyScalar(y));
            points.push(p);
        }
        const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
        const lineMat = new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.15 });
        const ring = new THREE.Line(lineGeo, lineMat);
        orbitGroup.add(ring);

        // 2. Create Logo Node (Texture on Circle)
        // Use a group for the node so we can offset/scale if needed
        const nodeGroup = new THREE.Group();
        orbitGroup.add(nodeGroup);

        // Circular Coin
        const geometry = new THREE.CircleGeometry(0.6, 32);
        const texture = textureLoader.load(d.img);
        
        // Fix texture encoding for better color
        texture.colorSpace = THREE.SRGBColorSpace; 

        const material = new THREE.MeshBasicMaterial({ 
            map: texture, 
            transparent: true,
            side: THREE.DoubleSide,
            color: 0xffffff // White tint to show original colors
        });
        
        const mesh = new THREE.Mesh(geometry, material);
        nodeGroup.add(mesh);
        
        // Add a glow ring behind the logo
        const glowGeo = new THREE.CircleGeometry(0.65, 32);
        const glowMat = new THREE.MeshBasicMaterial({ color: d.color, transparent: true, opacity: 0.5 });
        const glowMesh = new THREE.Mesh(glowGeo, glowMat);
        glowMesh.position.z = -0.01; // Slightly behind
        nodeGroup.add(glowMesh);

        orbitNodes.push({
            group: nodeGroup, // Move the group, not just mesh
            mesh: mesh,       // Ref to mesh if we need to rotate it specifically
            labelId: d.id,
            angle: Math.random() * Math.PI * 2,
            speed: d.speed,
            radius: d.radius,
            u, v
        });
    });
};

const updateCardAnimation = (time) => {
    if (!cardRenderer || !orbitGroup) return;
    
    // Rotate entire group slightly
    orbitGroup.rotation.y = time * 0.1;
    orbitGroup.updateMatrixWorld(); 

    const parentInv = orbitGroup.quaternion.clone().invert();
    const camQuat = cardCamera.quaternion;

    // Update Nodes
    orbitNodes.forEach(node => {
        // Move angle
        node.angle += node.speed * 0.01;
        
        // Calculate position
        const x = Math.cos(node.angle) * node.radius;
        const y = Math.sin(node.angle) * node.radius;
        const pos = node.u.clone().multiplyScalar(x).add(node.v.clone().multiplyScalar(y));
        
        // Update Group Position
        node.group.position.copy(pos);
        
        // BILLBOARDING: Force the logo to always face the camera by canceling parent rotation
        node.group.quaternion.copy(parentInv).multiply(camQuat);
        
        // Update Label Position (Project 3D -> 2D)
        const vector = pos.clone();
        vector.applyMatrix4(orbitGroup.matrixWorld); 
        vector.project(cardCamera);

        const cx = (vector.x * .5 + .5) * cardWidth.value;
        const cy = -(vector.y * .5 - .5) * cardHeight.value;

        // Find label element
        const el = document.getElementById(`label-${node.labelId}`);
        if (el) {
            if (vector.z < 1) {
                el.style.display = 'block';
                el.style.transform = `translate(-50%, -50%) translate(${cx}px, ${cy}px) translateY(-30px)`; 
                
                const opacity = 1.0 - Math.max(0, (vector.z - 0.5) * 2); 
                el.style.opacity = Math.max(0.2, opacity);
                el.style.zIndex = vector.z < 0.5 ? 10 : 1;
            } else {
                el.style.display = 'none';
            }
        }
    });

    cardRenderer.render(cardScene, cardCamera);
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

    // Card Update
    if (cardRenderer) {
        updateCardAnimation(time);
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

    // Card Resize (if container changes size)
    if (cardCanvasContainer.value) {
        const cw = cardCanvasContainer.value.clientWidth;
        const ch = cardCanvasContainer.value.clientHeight;
        cardWidth.value = cw;
        cardHeight.value = ch;
        cardCamera.aspect = cw / ch;
        cardCamera.updateProjectionMatrix();
        cardRenderer.setSize(cw, ch);
    }
};

onMounted(() => {
    initBackgroundThree();
    // Delay card init slightly to ensure DOM dimensions
    setTimeout(() => {
        initCardThree();
    }, 100);
    startTextAnimation();
    animate();
    window.addEventListener('resize', onResize);
    window.addEventListener('mousemove', onMouseMove);
});

onUnmounted(() => {
    cancelAnimationFrame(bgFrameId);
    window.removeEventListener('resize', onResize);
    window.removeEventListener('mousemove', onMouseMove);
    if(bgRenderer) bgRenderer.dispose();
    if(cardRenderer) cardRenderer.dispose();
    if(scrambleInterval) clearInterval(scrambleInterval);
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
