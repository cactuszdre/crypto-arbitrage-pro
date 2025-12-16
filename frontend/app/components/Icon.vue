<template>
  <ClientOnly>
    <span class="iconify" :data-icon="name" :data-width="size" v-bind="$attrs"></span>
    <template #fallback>
      <span class="inline-block" :style="{ width: size + 'px', height: size + 'px' }"></span>
    </template>
  </ClientOnly>
</template>

<script setup lang="ts">
defineProps<{
  name: string
  size?: number | string
}>()
</script>
