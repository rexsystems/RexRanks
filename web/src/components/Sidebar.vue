<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { changeCount, store } from "../store";

const route = useRoute();
const router = useRouter();

function active(prefix: string) {
  return route.path === prefix || route.path.startsWith(prefix + "/");
}

function go(path: string) {
  router.push(path);
}

const session = () => store.session;
</script>

<template>
  <aside class="sidebar">
    <a class="brand" href="#/groups">
      <svg class="brand-mark" viewBox="0 0 64 64" aria-hidden="true">
        <rect width="64" height="64" rx="14" fill="#0c0d10" />
        <path fill="#e8b84a" d="M16 14h22.5c8.5 0 14.5 5.4 14.5 13.2 0 5.6-3.2 10-8.2 11.9L52 50h-9.6L35.2 39.2H24.5V50H16V14zm8.5 7.3v10.7h13c4.1 0 6.7-2.2 6.7-5.4s-2.6-5.3-6.7-5.3h-13z"/>
      </svg>
      <span>
        <div class="brand-name">RexRanks</div>
        <div class="brand-sub">Editor</div>
      </span>
    </a>
    <nav class="nav">
      <a href="#/groups" :class="{ active: active('/groups') }" title="Groups">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M7 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm10 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM7 12c-3 0-5 2-5 4.5V20h10v-3.5C12 14 10 12 7 12Zm10 0c-.7 0-1.4.1-2 .4 1.6 1 2.5 2.6 2.5 4.6V20H22v-3.5C22 14 20 12 17 12Z"/></svg>
        <span class="nav-label">Groups</span>
      </a>
      <a href="#/users" :class="{ active: active('/users') }" title="Users">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="3.2"/><path d="M5 19c0-3.3 3.1-5.5 7-5.5s7 2.2 7 5.5"/></svg>
        <span class="nav-label">Users</span>
      </a>
      <a href="#/tracks" :class="{ active: active('/tracks') }" title="Tracks">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M4 12h10M4 17h7"/><circle cx="18" cy="17" r="3"/></svg>
        <span class="nav-label">Tracks</span>
      </a>
      <a href="#/color" :class="{ active: active('/color') }" title="Colour studio">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3a9 9 0 1 0 0 18c.8 0 1.2-.9.7-1.5a2.4 2.4 0 0 1 1.8-3.9H16a5 5 0 0 0 0-10h-4Z"/><circle cx="7.5" cy="10" r="1" fill="currentColor"/><circle cx="10.5" cy="7.2" r="1" fill="currentColor"/><circle cx="14.5" cy="7.8" r="1" fill="currentColor"/></svg>
        <span class="nav-label">Colour studio</span>
      </a>
      <a href="#/search" :class="{ active: active('/search') }" title="Search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="6.5"/><path d="M16 16l5 5"/></svg>
        <span class="nav-label">Search</span>
      </a>
      <a href="#/changes" :class="{ active: active('/changes') }" title="Changes">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
        <span class="nav-label">Changes</span>
        <span v-if="changeCount" class="badge">{{ changeCount }}</span>
      </a>
    </nav>
    <div class="side-meta">
      <div><strong>{{ session()?.server || "—" }}</strong></div>
      <div>v{{ session()?.version || "—" }}</div>
      <div v-if="session()?.mode === 'demo'">Demo session</div>
      <div v-else-if="session()?.createdBy">Opened by {{ session()?.createdBy }}</div>
    </div>
  </aside>
</template>
