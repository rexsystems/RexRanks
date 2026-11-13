<script setup lang="ts">
import { computed, ref } from "vue";
import { applyChanges, changeCount, discardChanges, dirty, proposed, store, toast } from "../store";

const applying = ref(false);
const summary = ref(store.lastApply);

const payload = computed(() => proposed());
const isDemo = computed(() => store.session?.mode === "demo");
const isRemote = computed(() => store.session?.mode === "remote");
const applyCode = computed(() => store.session?.applyCode);

async function apply() {
  applying.value = true;
  try {
    summary.value = await applyChanges();
  } catch (e) {
    toast("error", e instanceof Error ? e.message : String(e));
  } finally {
    applying.value = false;
  }
}

function discard() {
  discardChanges();
  toast("info", "Reverted to the last loaded snapshot.");
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Changes</h1>
        <p v-if="dirty">{{ changeCount }} subject{{ changeCount === 1 ? "" : "s" }} differ from the loaded snapshot.</p>
        <p v-else>No unapplied changes.</p>
      </div>
      <div class="page-actions">
        <button class="btn" :disabled="!dirty" @click="discard">Discard</button>
        <button class="btn primary" :disabled="!dirty || applying" @click="apply">
          {{ isRemote ? "Save for /rr apply" : isDemo ? "Keep locally" : "Apply now" }}
        </button>
      </div>
    </div>

    <div class="card card-pad" v-if="isRemote || isDemo" style="margin-bottom:16px">
      <p v-if="isRemote" style="margin:0">
        This session is hosted remotely. Saving records a pending payload — run
        <span class="mono">/rr apply {{ applyCode || "&lt;code&gt;" }}</span>
        on the server to take it live.
      </p>
      <p v-else style="margin:0">
        You're in a demo session. The colour studio and editors work in full; applying keeps the changes in this browser only.
        Open <span class="mono">/rr editor</span> on a server for a live link.
      </p>
    </div>

    <div v-if="summary" class="card card-pad" style="margin-bottom:16px;border-color:var(--gold-line)">
      {{ summary }}
    </div>

    <div class="card card-pad">
      <div style="font-family:var(--display);font-weight:700;margin-bottom:10px">Proposed payload</div>
      <pre class="mono" style="margin:0;white-space:pre-wrap;font-size:11.5px;color:var(--muted);max-height:480px;overflow:auto">{{ JSON.stringify(payload, null, 2) }}</pre>
    </div>
  </div>
</template>
