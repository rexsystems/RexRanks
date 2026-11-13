<script setup lang="ts">
import { ref } from "vue";
import { runSearch } from "../store";

const q = ref("");
const results = ref<{ subject: string; node: string }[]>([]);
const count = ref<number | null>(null);
const running = ref(false);

async function go() {
  running.value = true;
  try {
    const r = await runSearch(q.value);
    results.value = r.hits;
    count.value = r.count;
  } finally {
    running.value = false;
  }
}

function href(subject: string): string {
  if (subject.startsWith("group:")) return "#/groups/" + subject.slice(6);
  if (subject.startsWith("user:")) return "#/users";
  return "#/search";
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Search</h1>
        <p>Find a permission, prefix or shorthand across every loaded subject.</p>
      </div>
    </div>
    <form class="card card-pad" style="display:flex;gap:8px;max-width:640px" @submit.prevent="go">
      <input v-model="q" type="search" placeholder="essentials.fly, prefix, group.admin…" />
      <button class="btn primary" type="submit" :disabled="running">Search</button>
    </form>
    <div v-if="count !== null" class="card card-pad" style="margin-top:16px">
      <div style="color:var(--muted);margin-bottom:8px">{{ count }} hit{{ count === 1 ? "" : "s" }}</div>
      <table class="table" v-if="results.length">
        <thead><tr><th>Subject</th><th>Node</th></tr></thead>
        <tbody>
          <tr v-for="(h, i) in results" :key="i">
            <td><a :href="href(h.subject)">{{ h.subject }}</a></td>
            <td class="mono">{{ h.node }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty" style="padding:20px">Nothing matched.</div>
    </div>
  </div>
</template>
