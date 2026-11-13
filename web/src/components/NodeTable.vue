<script setup lang="ts">
import { computed, ref } from "vue";
import type { RankNode, NodeType } from "../types";
import { nodeShorthand } from "../types";
import { store } from "../store";

const props = defineProps<{
  nodes: RankNode[];
}>();
const emit = defineEmits<{
  (e: "update", nodes: RankNode[]): void;
}>();

const draft = ref("");
const type = ref<NodeType>("permission");
const value = ref("true");
const expiryText = ref("");
const ctxKey = ref("");
const ctxVal = ref("");
const editing = ref<number | null>(null);

const known = computed(() => store.knownNodes || []);

function pillClass(t: NodeType) {
  if (t === "permission" || t === "regex_permission") return "perm";
  if (t === "inheritance") return "parent";
  if (t === "prefix" || t === "suffix") return "chat";
  return "meta";
}

function formatExpiry(n: number): string {
  if (!n) return "—";
  const ms = n - Date.now();
  if (ms <= 0) return "expired";
  const s = Math.round(ms / 1000);
  if (s < 60) return s + "s";
  if (s < 3600) return Math.round(s / 60) + "m";
  if (s < 86400) return Math.round(s / 3600) + "h";
  return Math.round(s / 86400) + "d";
}

function parseDuration(raw: string): number {
  const t = raw.trim();
  if (!t) return 0;
  const re = /(\d+)\s*(w|d|h|m|s)/gi;
  let sec = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(t))) {
    const n = parseInt(m[1]!, 10);
    switch (m[2]!.toLowerCase()) {
      case "w": sec += n * 7 * 86400; break;
      case "d": sec += n * 86400; break;
      case "h": sec += n * 3600; break;
      case "m": sec += n * 60; break;
      case "s": sec += n; break;
    }
  }
  return sec > 0 ? Date.now() + sec * 1000 : 0;
}

function contextsOf(node: RankNode): [string, string][] {
  const out: [string, string][] = [];
  if (!node.contexts) return out;
  for (const [k, vals] of Object.entries(node.contexts)) {
    for (const v of vals) out.push([k, v]);
  }
  return out;
}

function add() {
  const key = draft.value.trim();
  if (!key) return;
  let node: RankNode = {
    type: type.value,
    key,
    value: value.value,
    negated: value.value === "false",
    expiry: parseDuration(expiryText.value),
  };
  if (type.value === "permission" && (key.startsWith("group.") || key.startsWith("prefix.") || key.startsWith("-"))) {
    // Allow shorthand in the permission box.
    node = shorthandToNode(key, node.expiry);
  }
  if (ctxKey.value && ctxVal.value) {
    node.contexts = { [ctxKey.value]: [ctxVal.value] };
  }
  emit("update", [...props.nodes, node]);
  draft.value = "";
  expiryText.value = "";
  ctxKey.value = "";
  ctxVal.value = "";
}

function shorthandToNode(raw: string, expiry: number): RankNode {
  let s = raw;
  let negated = false;
  if (s.startsWith("-")) {
    negated = true;
    s = s.slice(1);
  }
  const ps = /^(prefix|suffix)\.(-?\d+)\.(.*)$/i.exec(s);
  if (ps) return { type: ps[1]!.toLowerCase() as NodeType, key: ps[2]!, value: ps[3]!, negated: false, expiry };
  const g = /^group\.([a-z0-9_\-]+)$/i.exec(s);
  if (g) return { type: "inheritance", key: g[1]!.toLowerCase(), value: "true", negated: false, expiry };
  const w = /^weight\.(-?\d+)$/i.exec(s);
  if (w) return { type: "weight", key: "weight", value: w[1]!, negated: false, expiry };
  const m = /^meta\.([^.]+)\.(.*)$/i.exec(s);
  if (m) return { type: "meta", key: m[1]!, value: m[2]!, negated: false, expiry };
  if (s.startsWith("r=") || s.startsWith("R=")) {
    return { type: "regex_permission", key: s.slice(2), value: negated ? "false" : "true", negated, expiry };
  }
  return { type: "permission", key: s.toLowerCase(), value: negated ? "false" : "true", negated, expiry };
}

function remove(i: number) {
  emit("update", props.nodes.filter((_, idx) => idx !== i));
}

function toggle(i: number) {
  const next = props.nodes.map((n, idx) => {
    if (idx !== i) return n;
    const negated = !n.negated;
    return { ...n, negated, value: n.type === "permission" || n.type === "regex_permission" ? String(!negated) : n.value };
  });
  emit("update", next);
}

function setExpiry(i: number, raw: string) {
  const expiry = parseDuration(raw);
  emit("update", props.nodes.map((n, idx) => (idx === i ? { ...n, expiry } : n)));
  editing.value = null;
}
</script>

<template>
  <div>
    <table class="table" v-if="nodes.length">
      <thead>
        <tr>
          <th>Type</th>
          <th>Node</th>
          <th>Value</th>
          <th>Contexts</th>
          <th>Expiry</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(node, i) in nodes" :key="i">
          <td><span class="pill" :class="pillClass(node.type)">{{ node.type.replace('_', ' ') }}</span></td>
          <td class="mono">{{ node.type === 'permission' || node.type === 'regex_permission' ? (node.negated ? '-' : '') + node.key : nodeShorthand(node) }}</td>
          <td>
            <button v-if="node.type === 'permission' || node.type === 'regex_permission'" class="btn small" @click="toggle(i)">
              {{ node.negated ? 'false' : 'true' }}
            </button>
            <span v-else class="mono">{{ node.value }}</span>
          </td>
          <td>
            <span v-for="([k, v], ci) in contextsOf(node)" :key="ci" class="ctx">{{ k }}={{ v }}</span>
            <span v-if="!contextsOf(node).length" style="color:var(--faint)">—</span>
          </td>
          <td>
            <button class="btn small ghost" @click="editing = editing === i ? null : i">{{ formatExpiry(node.expiry) }}</button>
            <input v-if="editing === i" class="input" style="margin-top:6px" placeholder="1d2h" @keydown.enter="setExpiry(i, ($event.target as HTMLInputElement).value)" />
          </td>
          <td style="text-align:right">
            <button class="btn small danger" @click="remove(i)">Remove</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty" style="padding:24px"><p>No nodes yet.</p></div>

    <form class="card card-pad" style="margin-top:16px;display:grid;gap:10px" @submit.prevent="add">
      <div style="font-weight:650;font-family:var(--display)">Add a node</div>
      <div class="kvs">
        <div>
          <label class="lbl">Type</label>
          <select v-model="type">
            <option value="permission">Permission</option>
            <option value="regex_permission">Regex permission</option>
            <option value="inheritance">Inheritance</option>
            <option value="prefix">Prefix</option>
            <option value="suffix">Suffix</option>
            <option value="meta">Meta</option>
            <option value="weight">Weight</option>
            <option value="display_name">Display name</option>
          </select>
        </div>
        <div>
          <label class="lbl">Value</label>
          <select v-if="type === 'permission' || type === 'regex_permission'" v-model="value">
            <option value="true">true</option>
            <option value="false">false</option>
          </select>
          <input v-else v-model="value" placeholder="value or MiniMessage" />
        </div>
        <div>
          <label class="lbl">Key / shorthand</label>
          <input v-model="draft" list="known-nodes" placeholder="essentials.fly  or  group.admin  or  prefix.100.&lt;#ffaa00&gt;[VIP] " />
          <datalist id="known-nodes">
            <option v-for="k in known" :key="k" :value="k" />
          </datalist>
        </div>
        <div>
          <label class="lbl">Temporary</label>
          <input v-model="expiryText" placeholder="leave empty, or 1d / 2h30m" />
        </div>
        <div>
          <label class="lbl">Context key</label>
          <input v-model="ctxKey" placeholder="world" />
        </div>
        <div>
          <label class="lbl">Context value</label>
          <input v-model="ctxVal" placeholder="nether" />
        </div>
      </div>
      <div><button class="btn primary" type="submit">Add node</button></div>
    </form>
  </div>
</template>
