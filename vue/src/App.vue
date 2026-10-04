<script setup lang="ts">
</script>

<template>
  <router-view v-slot="{ Component, route }">
    <transition name="page-fade" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<style>
.page-fade-enter-active {
  transition: opacity 250ms ease, transform 250ms ease;
}
.page-fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
