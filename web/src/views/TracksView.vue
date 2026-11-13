<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { createTrack, deleteTrack, groupList, store, toast, trackList, upsertTrack } from "../store";

const route = useRoute();
const router = useRouter();
const tracks = computed(() => trackList());
const name = computed(() => String(route.params.name || tracks.value[0]?.name || ""));
const track = computed(() => (store.data.tracks[name.value] ? { name: name.value, ...store.data.tracks[name.value]! } : null));
const newName = ref("");
const addGroup = ref("");
const groups = computed(() => groupList());

watch(
  () => [route.params.name, tracks.value.length],
  () => {
    if (!route.params.name && tracks.value[0]) router.replace("/tracks/" + tracks.value[0].name);
  },
  { immediate: true },
);

function create() {
  try {
    const id = createTrack(newName.value);
    newName.value = "";
    router.push("/tracks/" + id);
    toast("ok", "Created track " + id + ".");
  } catch (e) {
    toast("error", e instanceof Error ? e.message : String(e));
  }
}
function remove() {
  if (!track.value) return;
  if (!confirm("Delete track " + track.value.name + "?")) return;
  deleteTrack(track.value.name);
  toast("ok", "Deleted track.");
  router.push("/tracks");
}
function setGroups(gs: string[]) {
  if (!track.value) return;
  upsertTrack(track.value.name, gs);
}
function append() {
  if (!track.value || !addGroup.value) return;
  if (track.value.groups.includes(addGroup.value)) {
    toast("error", "Already on this track.");
    return;
  }
  setGroups([...track.value.groups, addGroup.value]);
  addGroup.value = "";
}
function move(i: number, dir: number) {
  if (!track.value) return;
  const j = i + dir;
  if (j < 0 || j >= track.value.groups.length) return;
  const gs = [...track.value.groups];
  const [it] = gs.splice(i, 1);
  gs.splice(j, 0, it!);
  setGroups(gs);
}
function drop(i: number) {
  if (!track.value) return;
  setGroups(track.value.groups.filter((_, idx) => idx !== i));
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Tracks</h1>
        <p>Promotion ladders used by <span class="mono">/rr user &lt;name&gt; promote</span>.</p>
      </div>
    </div>
    <div class="split">
      <div class="card card-pad">
        <form @submit.prevent="create" style="display:flex;gap:6px;margin-bottom:12px">
          <input v-model="newName" placeholder="new track name" />
          <button class="btn primary" type="submit">Create</button>
        </form>
        <div class="list">
          <a
            v-for="t in tracks"
            :key="t.name"
            class="list-item"
            :class="{ active: t.name === name }"
            :href="'#/tracks/' + t.name"
            @click.prevent="router.push('/tracks/' + t.name)"
          >
            <strong>{{ t.name }}</strong>
            <span class="meta">{{ t.groups.length }}</span>
          </a>
        </div>
      </div>
      <div v-if="track" class="card card-pad">
        <div style="display:flex;justify-content:space-between;margin-bottom:16px">
          <h2 style="margin:0;font-family:var(--display)">{{ track.name }}</h2>
          <button class="btn danger" @click="remove">Delete</button>
        </div>
        <div class="track-lane" v-if="track.groups.length">
          <div v-for="(g, i) in track.groups" :key="g + i" class="track-step">
            <div class="pill">{{ i + 1 }}</div>
            <div style="font-weight:700;margin:8px 0 10px;font-size:18px;font-family:var(--display)">{{ g }}</div>
            <div style="display:flex;gap:4px">
              <button class="btn small" :disabled="i === 0" @click="move(i, -1)">←</button>
              <button class="btn small" :disabled="i === track.groups.length - 1" @click="move(i, 1)">→</button>
              <button class="btn small danger" @click="drop(i)">Remove</button>
            </div>
          </div>
        </div>
        <div v-else class="empty" style="padding:20px">This track is empty. Append a group.</div>
        <form @submit.prevent="append" style="display:flex;gap:8px;margin-top:16px;max-width:420px">
          <select v-model="addGroup">
            <option value="" disabled>Append a group…</option>
            <option v-for="g in groups" :key="g.name" :value="g.name">{{ g.name }}</option>
          </select>
          <button class="btn primary" type="submit">Append</button>
        </form>
      </div>
      <div v-else class="empty card"><h3>No track selected</h3></div>
    </div>
  </div>
</template>
