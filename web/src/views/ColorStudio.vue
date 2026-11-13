<script setup lang="ts">
import { computed, ref, watch } from "vue";
import ColorPicker from "../components/ColorPicker.vue";
import GradientBar from "../components/GradientBar.vue";
import MinecraftPreview from "../components/MinecraftPreview.vue";
import {
  colorText,
  cssGradient,
  defaultStops,
  distributeStops,
  evenly,
  gradientLegacy,
  gradientMiniMessage,
  harmonies,
  newId,
  parseColorInput,
  preciseMiniMessage,
  rainbowLegacy,
  rainbowMiniMessage,
  rainbowText,
  randomHex,
  reverseStops,
  wrapDecorations,
  type Decorations,
  type Easing,
  type GradientStop,
  type StudioMode,
} from "../color/engine";
import { PRESETS, cloneStops } from "../color/presets";
import { applyChatMeta, groupList, store, toast, userList } from "../store";

const mode = ref<StudioMode>("gradient");
const stops = ref<GradientStop[]>(defaultStops());
const selectedId = ref<string | null>(stops.value[0]?.id ?? null);
const easing = ref<Easing>("linear");
const space = ref<"rgb" | "hsv">("rgb");
const text = ref("VIP");
const backwards = ref(false);
const sat = ref(100);
const bri = ref(100);
const phase = ref(0);
const deco = ref<Decorations>({
  bold: false,
  italic: false,
  underlined: false,
  strikethrough: false,
  obfuscated: false,
});
const preview = ref<"chat" | "tab" | "nametag">("chat");
const importText = ref("");
const applyKind = ref<"group" | "user">("group");
const applyId = ref("");
const applyWhich = ref<"prefix" | "suffix">("prefix");
const applyPriority = ref(100);

const selected = computed(() => stops.value.find((s) => s.id === selectedId.value) ?? stops.value[0] ?? null);

watch(space, (s) => {
  stops.value = stops.value.map((st) => ({ ...st, space: s }));
});

const chars = computed(() => {
  const sample = text.value || "RexRanks";
  if (mode.value === "rainbow") {
    return rainbowText(sample + " ", backwards.value, sat.value, bri.value, phase.value / 100);
  }
  if (mode.value === "solid") {
    const hex = selected.value?.hex ?? "#ffffff";
    return [...(sample + " ")].map((char) => ({
      char,
      hex,
      visible: !/\s/.test(char),
    }));
  }
  return colorText(sample + " ", stops.value, easing.value);
});

const mmCompact = computed(() => {
  const t = text.value || "RexRanks";
  let inner: string;
  if (mode.value === "solid") inner = `<${selected.value?.hex ?? "#ffffff"}>${t}</${selected.value?.hex ?? "#ffffff"}>`;
  else if (mode.value === "rainbow") inner = rainbowMiniMessage(t, backwards.value);
  else inner = gradientMiniMessage(stops.value.map((s) => s.hex), t);
  return wrapDecorations(inner, deco.value);
});

const mmPrecise = computed(() => {
  const withoutSpace = chars.value.filter((_, i) => i < chars.value.length - 1 || chars.value[i]!.char !== " ");
  // The trailing space was for preview "VIP Steve" — strip last space for output.
  const trimmed = [...colorOrRainbow(text.value || "RexRanks")];
  return wrapDecorations(preciseMiniMessage(trimmed), deco.value);
});

function colorOrRainbow(t: string) {
  if (mode.value === "rainbow") return rainbowText(t, backwards.value, sat.value, bri.value, phase.value / 100);
  if (mode.value === "solid") {
    const hex = selected.value?.hex ?? "#ffffff";
    return [...t].map((char) => ({ char, hex, visible: !/\s/.test(char) }));
  }
  return colorText(t, stops.value, easing.value);
}

const legacy = computed(() => {
  const t = text.value || "RexRanks";
  if (mode.value === "solid") return "&#" + (selected.value?.hex ?? "#ffffff").slice(1) + t;
  if (mode.value === "rainbow") return rainbowLegacy(t, backwards.value, sat.value, bri.value, phase.value / 100);
  return gradientLegacy(stops.value, t, easing.value);
});

const barCss = computed(() => {
  if (mode.value === "rainbow") {
    return "linear-gradient(90deg,#ff0000,#ffff00,#00ff00,#00ffff,#0000ff,#ff00ff,#ff0000)";
  }
  return cssGradient(stops.value, easing.value);
});

function setSelectedHex(hex: string) {
  if (!selected.value) return;
  stops.value = stops.value.map((s) => (s.id === selected.value!.id ? { ...s, hex } : s));
}

function addStop() {
  const id = newId();
  stops.value = [...stops.value, { id, position: 1, hex: randomHex(), space: space.value }].sort((a, b) => a.position - b.position);
  stops.value = distributeStops(stops.value);
  selectedId.value = id;
  mode.value = "gradient";
}

function removeStop() {
  if (stops.value.length <= 1) return;
  const id = selectedId.value;
  stops.value = stops.value.filter((s) => s.id !== id);
  selectedId.value = stops.value[0]?.id ?? null;
}

function applyPreset(id: string) {
  const p = PRESETS.find((x) => x.id === id);
  if (!p) return;
  stops.value = cloneStops(p.stops).map((s) => ({ ...s, space: space.value }));
  selectedId.value = stops.value[0]?.id ?? null;
  mode.value = stops.value.length > 1 ? "gradient" : "solid";
}

function doImport() {
  const parsed = parseColorInput(importText.value);
  if (!parsed) {
    toast("error", "Couldn't parse that colour string.");
    return;
  }
  mode.value = parsed.mode;
  if (parsed.stops.length) {
    stops.value = parsed.stops;
    selectedId.value = stops.value[0]?.id ?? null;
  }
  if (parsed.text) text.value = parsed.text;
  backwards.value = parsed.backwards;
  deco.value = { ...parsed.decorations };
  toast("ok", "Imported " + parsed.mode + ".");
}

async function copy(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value);
    toast("ok", "Copied " + label + ".");
  } catch {
    toast("error", "Clipboard is unavailable — select the text and copy it yourself.");
  }
}

function applyHarmony(kind: string) {
  const hex = selected.value?.hex ?? "#e8b84a";
  const set = harmonies(hex)[kind];
  if (!set) return;
  stops.value = evenly(set).map((s) => ({ ...s, space: space.value }));
  selectedId.value = stops.value[0]?.id ?? null;
  mode.value = stops.value.length > 1 ? "gradient" : "solid";
}

function doApply() {
  if (!applyId.value) {
    toast("error", "Pick a group or user to apply to.");
    return;
  }
  try {
    applyChatMeta(applyKind.value, applyId.value, applyWhich.value, mmCompact.value, applyPriority.value);
  } catch (e) {
    toast("error", e instanceof Error ? e.message : String(e));
  }
}

const groups = computed(() => groupList());
const users = computed(() => userList());
const hasSession = computed(() => !!store.session && store.session.mode !== "demo");
</script>

<template>
  <div class="studio-page">
    <div class="page-head">
      <div>
        <h1>Colour studio</h1>
        <p>Build multi-stop gradients, rainbows and solids — then copy MiniMessage or apply them as a prefix.</p>
      </div>
      <div class="mode-toggle">
        <button :class="{ active: mode === 'solid' }" @click="mode = 'solid'">Solid</button>
        <button :class="{ active: mode === 'gradient' }" @click="mode = 'gradient'">Gradient</button>
        <button :class="{ active: mode === 'rainbow' }" @click="mode = 'rainbow'">Rainbow</button>
      </div>
    </div>

    <div class="preview-stage">
      <div style="position:relative;z-index:1;display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <div class="mode-toggle">
          <button :class="{ active: preview === 'chat' }" @click="preview = 'chat'">Chat</button>
          <button :class="{ active: preview === 'tab' }" @click="preview = 'tab'">Tab list</button>
          <button :class="{ active: preview === 'nametag' }" @click="preview = 'nametag'">Nametag</button>
        </div>
      </div>
      <MinecraftPreview :chars="chars" :decorations="deco" :variant="preview" />
    </div>

    <GradientBar
      v-if="mode !== 'rainbow'"
      :stops="stops"
      :selected-id="selectedId"
      :easing="easing"
      @update:stops="stops = $event"
      @update:selected-id="selectedId = $event"
    />
    <div v-else class="grad-bar-wrap">
      <div class="grad-bar" :style="{ background: barCss }" />
    </div>

    <div class="studio-grid" style="margin-top:22px">
      <div>
        <div class="card card-pad" style="margin-bottom:16px">
          <div class="kvs">
            <div style="grid-column:1 / -1">
              <label class="lbl">Sample text</label>
              <input v-model="text" placeholder="VIP  ·  Owner  ·  RexRanks" />
            </div>
            <div>
              <label class="lbl">Decorations</label>
              <div class="deco-toggles">
                <button :class="{ active: deco.bold }" title="Bold" @click="deco.bold = !deco.bold"><b>B</b></button>
                <button :class="{ active: deco.italic }" title="Italic" @click="deco.italic = !deco.italic"><i>I</i></button>
                <button :class="{ active: deco.underlined }" title="Underline" @click="deco.underlined = !deco.underlined"><u>U</u></button>
                <button :class="{ active: deco.strikethrough }" title="Strikethrough" @click="deco.strikethrough = !deco.strikethrough"><s>S</s></button>
                <button :class="{ active: deco.obfuscated }" title="Obfuscated / magic" @click="deco.obfuscated = !deco.obfuscated">§k</button>
              </div>
            </div>
            <div v-if="mode === 'gradient'">
              <label class="lbl">Interpolation</label>
              <select v-model="space">
                <option value="rgb">RGB (straight mix)</option>
                <option value="hsv">HSV (around the colour wheel)</option>
              </select>
            </div>
            <div v-if="mode === 'gradient'">
              <label class="lbl">Easing (precise / legacy)</label>
              <select v-model="easing">
                <option value="linear">Linear</option>
                <option value="ease-in">Ease in</option>
                <option value="ease-out">Ease out</option>
                <option value="ease-in-out">Ease in-out</option>
                <option value="sine">Sine</option>
              </select>
            </div>
            <template v-if="mode === 'rainbow'">
              <div>
                <label class="lbl">Saturation {{ sat }}</label>
                <input type="range" min="0" max="100" v-model.number="sat" />
              </div>
              <div>
                <label class="lbl">Brightness {{ bri }}</label>
                <input type="range" min="0" max="100" v-model.number="bri" />
              </div>
              <div>
                <label class="lbl">Phase {{ phase }}</label>
                <input type="range" min="0" max="100" v-model.number="phase" />
              </div>
              <div>
                <label class="lbl">Direction</label>
                <button class="btn" @click="backwards = !backwards">{{ backwards ? "Backwards" : "Forwards" }}</button>
              </div>
            </template>
          </div>
          <div v-if="mode !== 'rainbow'" style="display:flex;gap:8px;flex-wrap:wrap;margin-top:14px">
            <button class="btn small" @click="addStop">Add stop</button>
            <button class="btn small" :disabled="stops.length <= 1" @click="removeStop">Remove stop</button>
            <button class="btn small" @click="stops = distributeStops(stops)">Distribute</button>
            <button class="btn small" @click="stops = reverseStops(stops)">Reverse</button>
            <button class="btn small" @click="applyHarmony('complementary')">Complement</button>
            <button class="btn small" @click="applyHarmony('analogous')">Analogous</button>
            <button class="btn small" @click="applyHarmony('triadic')">Triadic</button>
          </div>
        </div>

        <div class="card card-pad" style="margin-bottom:16px">
          <div style="font-family:var(--display);font-weight:700;margin-bottom:12px">Output</div>
          <div class="output-row">
            <span class="lbl" style="margin:0">MiniMessage</span>
            <code>{{ mmCompact }}</code>
            <button class="btn small" @click="copy(mmCompact, 'MiniMessage')">Copy</button>
          </div>
          <div class="output-row">
            <span class="lbl" style="margin:0">Precise</span>
            <code>{{ mmPrecise }}</code>
            <button class="btn small" @click="copy(mmPrecise, 'precise MiniMessage')">Copy</button>
          </div>
          <div class="output-row">
            <span class="lbl" style="margin:0">Legacy hex</span>
            <code>{{ legacy }}</code>
            <button class="btn small" @click="copy(legacy, 'legacy hex')">Copy</button>
          </div>
          <p style="color:var(--muted);font-size:12px;margin:8px 0 0">
            Compact MiniMessage spaces stops evenly — that's what the <span class="mono">&lt;gradient&gt;</span> tag supports.
            Precise and legacy honour drag positions and easing, character by character.
          </p>
        </div>

        <div class="card card-pad" style="margin-bottom:16px">
          <div style="font-family:var(--display);font-weight:700;margin-bottom:12px">Apply as prefix or suffix</div>
          <div class="kvs">
            <div>
              <label class="lbl">Kind</label>
              <select v-model="applyKind">
                <option value="group">Group</option>
                <option value="user">User</option>
              </select>
            </div>
            <div>
              <label class="lbl">Target</label>
              <select v-model="applyId">
                <option value="" disabled>Select…</option>
                <option v-if="applyKind === 'group'" v-for="g in groups" :key="g.name" :value="g.name">{{ g.name }}</option>
                <option v-if="applyKind === 'user'" v-for="u in users" :key="u.uuid" :value="u.uuid">{{ u.name }}</option>
              </select>
            </div>
            <div>
              <label class="lbl">Slot</label>
              <select v-model="applyWhich">
                <option value="prefix">Prefix</option>
                <option value="suffix">Suffix</option>
              </select>
            </div>
            <div>
              <label class="lbl">Priority</label>
              <input type="number" v-model.number="applyPriority" />
            </div>
          </div>
          <div style="margin-top:12px;display:flex;gap:8px;align-items:center">
            <button class="btn primary" @click="doApply">Apply to {{ applyWhich }}</button>
            <span v-if="!hasSession" style="color:var(--muted);font-size:12px">Demo: kept in this session until you open a live editor link.</span>
          </div>
        </div>

        <div class="card card-pad">
          <div style="font-family:var(--display);font-weight:700;margin-bottom:12px">Import</div>
          <textarea v-model="importText" rows="3" placeholder="Paste MiniMessage, &#RRGGBB, or a bare #hex…"></textarea>
          <button class="btn" style="margin-top:10px" @click="doImport">Parse into the studio</button>
        </div>
      </div>

      <div>
        <div class="card card-pad" style="margin-bottom:16px" v-if="mode !== 'rainbow' && selected">
          <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:10px">
            <div style="font-family:var(--display);font-weight:700">Stop colour</div>
            <div class="mono" style="color:var(--muted)">{{ Math.round((selected?.position ?? 0) * 100) }}%</div>
          </div>
          <ColorPicker :hex="selected.hex" @update:hex="setSelectedHex" />
        </div>

        <div class="card card-pad">
          <div style="font-family:var(--display);font-weight:700;margin-bottom:12px">Presets</div>
          <div class="preset-grid">
            <button
              v-for="p in PRESETS"
              :key="p.id"
              class="preset"
              @click="applyPreset(p.id)"
            >
              <div class="swatch" :style="{ background: cssGradient(p.stops) }" />
              <div class="name">{{ p.name }}</div>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
