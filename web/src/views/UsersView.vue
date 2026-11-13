<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import NodeTable from "../components/NodeTable.vue";
import { deleteUser, groupList, store, toast, upsertUser, userList } from "../store";
import type { RankNode } from "../types";

const route = useRoute();
const router = useRouter();
const q = ref("");
const users = computed(() => {
  const all = userList();
  const s = q.value.trim().toLowerCase();
  if (!s) return all;
  return all.filter((u) => u.name.toLowerCase().includes(s) || u.uuid.includes(s) || u.primaryGroup.includes(s));
});
const uuid = computed(() => String(route.params.uuid || users.value[0]?.uuid || ""));
const user = computed(() => store.data.users[uuid.value] || null);
const groups = computed(() => groupList());

watch(
  () => [route.params.uuid, users.value.length],
  () => {
    if (!route.params.uuid && users.value[0]) router.replace("/users/" + users.value[0].uuid);
  },
  { immediate: true },
);

function select(id: string) {
  router.push("/users/" + id);
}
function remove() {
  if (!user.value) return;
  if (!confirm("Delete user " + user.value.name + "?")) return;
  deleteUser(user.value.uuid);
  toast("ok", "Deleted user.");
  router.push("/users");
}
function setPrimary(g: string) {
  if (!user.value) return;
  upsertUser({ ...user.value, primaryGroup: g });
}
function setNodes(nodes: RankNode[]) {
  if (!user.value) return;
  upsertUser({ ...user.value, nodes });
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Users</h1>
        <p>{{ userList().length }} loaded users — primary group, parents and nodes.</p>
      </div>
    </div>
    <div class="split">
      <div class="card card-pad">
        <input v-model="q" type="search" placeholder="Search users…" style="margin-bottom:12px" />
        <div class="list">
          <a
            v-for="u in users"
            :key="u.uuid"
            class="list-item"
            :class="{ active: u.uuid === uuid }"
            :href="'#/users/' + u.uuid"
            @click.prevent="select(u.uuid)"
          >
            <strong>{{ u.name }}</strong>
            <span class="meta">{{ u.primaryGroup }}</span>
          </a>
          <div v-if="!users.length" class="empty" style="padding:20px">No users match.</div>
        </div>
      </div>
      <div v-if="user" class="card card-pad">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:16px">
          <div>
            <h2 style="margin:0;font-family:var(--display)">{{ user.name }}</h2>
            <div class="mono" style="color:var(--muted);font-size:12px">{{ user.uuid }}</div>
          </div>
          <button class="btn danger" @click="remove">Delete</button>
        </div>
        <div style="margin-bottom:18px;max-width:280px">
          <label class="lbl">Primary group</label>
          <select :value="user.primaryGroup" @change="setPrimary(($event.target as HTMLSelectElement).value)">
            <option v-for="g in groups" :key="g.name" :value="g.name">{{ g.name }}</option>
          </select>
        </div>
        <NodeTable :nodes="user.nodes" @update="setNodes" />
      </div>
      <div v-else class="empty card"><h3>No user selected</h3><p>Users appear once they have joined, or after an import.</p></div>
    </div>
  </div>
</template>
