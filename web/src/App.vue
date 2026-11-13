<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import Sidebar from "./components/Sidebar.vue";
import { store } from "./store";

const route = useRoute();
const isStudio = computed(() => route.path.startsWith("/color"));
</script>

<template>
  <div v-if="store.loading" class="loading">
    <div class="spinner" />
    <div>Opening the editor…</div>
  </div>
  <div v-else class="shell" :class="{ studio: isStudio }">
    <Sidebar />
    <main class="main">
      <router-view />
    </main>
    <div class="toasts">
      <div v-for="t in store.toasts" :key="t.id" class="toast" :class="t.kind">{{ t.text }}</div>
    </div>
  </div>
</template>
