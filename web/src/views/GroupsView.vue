<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import NodeTable from "../components/NodeTable.vue";
import { createGroup, deleteGroup, groupList, store, toast, upsertGroup } from "../store";
import type { RankNode } from "../types";

const route = useRoute();
const router = useRouter();
const groups = computed(() => groupList());
const name = computed(() => String(route.params.name || groups.value[0]?.name || ""));
const group = computed(() => store.data.groups[name.value] || null);
const newName = ref("");

watch(
  () => [route.params.name, groups.value.length],
  () => {
    if (!route.params.name && groups.value[0]) router.replace("/groups/" + groups.value[0].name);
  },
  { immediate: true },
);

function select(n: string) {
  router.push("/groups/" + n);
}

function create() {
  try {
    const id = createGroup(newName.value);
    newName.value = "";
    router.push("/groups/" + id);
    toast("ok", "Created group " + id + ".");
  } catch (e) {
    toast("error", e instanceof Error ? e.message : String(e));
  }
}

function remove() {
  if (!group.value) return;
  if (!confirm("Delete group " + group.value.name + "?")) return;
  try {
    const n = group.value.name;
    deleteGroup(n);
    toast("ok", "Deleted " + n + ".");
    router.push("/groups");
  } catch (e) {
    toast("error", e instanceof Error ? e.message : String(e));
  }
}

function setWeight(w: number) {
  if (!group.value) return;
  upsertGroup({ ...group.value, weight: w });
}
function setDisplay(d: string) {
  if (!group.value) return;
  upsertGroup({ ...group.value, displayName: d });
}
function setNodes(nodes: RankNode[]) {
  if (!group.value) return;
  upsertGroup({ ...group.value, nodes });
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Groups</h1>
        <p>{{ groups.length }} groups — weight, display name, parents and permissions.</p>
      </div>
    </div>
    <div class="split">
      <div class="card card-pad">
        <form @submit.prevent="create" style="display:flex;gap:6px;margin-bottom:12px">
          <input v-model="newName" placeholder="new group name" />
          <button class="btn primary" type="submit">Create</button>
        </form>
        <div class="list">
          <a
            v-for="g in groups"
            :key="g.name"
            class="list-item"
            :class="{ active: g.name === name }"
            :href="'#/groups/' + g.name"
            @click.prevent="select(g.name)"
          >
            <strong>{{ g.name }}</strong>
            <span class="meta">w{{ g.weight }} · {{ g.nodes.length }}</span>
          </a>
        </div>
      </div>
      <div v-if="group" class="card card-pad">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:16px">
          <div>
            <h2 style="margin:0;font-family:var(--display)">{{ group.name }}</h2>
            <div style="color:var(--muted);font-size:13px">{{ group.nodes.length }} nodes</div>
          </div>
          <button class="btn danger" :disabled="group.name === 'default'" @click="remove">Delete</button>
        </div>
        <div class="kvs" style="margin-bottom:18px">
          <div>
            <label class="lbl">Weight</label>
            <input type="number" :value="group.weight" @change="setWeight(Number(($event.target as HTMLInputElement).value))" />
          </div>
          <div>
            <label class="lbl">Display name (MiniMessage)</label>
            <input :value="group.displayName" @change="setDisplay(($event.target as HTMLInputElement).value)" placeholder="&lt;gold&gt;Admin" />
          </div>
        </div>
        <NodeTable :nodes="group.nodes" @update="setNodes" />
      </div>
      <div v-else class="empty card"><h3>No group selected</h3></div>
    </div>
  </div>
</template>
