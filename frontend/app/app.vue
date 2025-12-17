<template>
  <!-- YouTube-style loading bar -->
  <NuxtLoadingIndicator 
    :height="3"
    :duration="2000"
    :throttle="200"
    color="transparent"
  />
  
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

<script setup>
// App entry point - uses layouts and pages automatically
</script>

<style>
/* Custom loading indicator styling */
.nuxt-loading-indicator {
  background: linear-gradient(
    90deg,
    #10b981 0%,
    #10b981 80%,
    #34d399 90%,
    #6ee7b7 95%,
    rgba(110, 231, 183, 0.5) 100%
  ) !important;
  box-shadow: 
    0 0 10px rgba(16, 185, 129, 0.8),
    0 0 20px rgba(16, 185, 129, 0.4),
    0 0 30px rgba(16, 185, 129, 0.2);
}

/* Glow effect at the end of the bar */
.nuxt-loading-indicator::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 100px;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(52, 211, 153, 0.6) 50%,
    rgba(110, 231, 183, 1) 100%
  );
  filter: blur(3px);
  animation: pulse-glow 1s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% {
    opacity: 0.8;
  }
  50% {
    opacity: 1;
  }
}
</style>

