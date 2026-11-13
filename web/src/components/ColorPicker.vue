<script setup lang="ts">
import { computed, ref } from "vue";
import { hsvToRgb, parseHex, rgbToHsv, toHex, tryHex, type Hsv, type Rgb } from "../color/engine";

const props = defineProps<{ hex: string }>();
const emit = defineEmits<{ (e: "update:hex", hex: string): void }>();

const hsv = computed<Hsv>(() => {
  try {
    return rgbToHsv(parseHex(props.hex));
  } catch {
    return { h: 40, s: 70, v: 90 };
  }
});

const hexInput = computed({
  get: () => props.hex,
  set: (v: string) => {
    const n = tryHex(v);
    if (n) emit("update:hex", n);
  },
});

const rgb = computed(() => {
  try {
    return parseHex(props.hex);
  } catch {
    return { r: 232, g: 184, b: 74 };
  }
});

function setHsv(partial: Partial<Hsv>) {
  const next = { ...hsv.value, ...partial };
  emit("update:hex", toHex(hsvToRgb(next)));
}

function setRgb(partial: Partial<Rgb>) {
  emit("update:hex", toHex({ ...rgb.value, ...partial }));
}

const sv = ref<HTMLElement | null>(null);
function posFromEvent(ev: MouseEvent | TouchEvent) {
  const el = sv.value;
  if (!el) return;
  const r = el.getBoundingClientRect();
  const pt = "touches" in ev ? ev.touches[0] : ev;
  if (!pt) return;
  const s = Math.max(0, Math.min(1, (pt.clientX - r.left) / r.width)) * 100;
  const v = Math.max(0, Math.min(1, 1 - (pt.clientY - r.top) / r.height)) * 100;
  setHsv({ s, v });
}
function startSv(ev: MouseEvent | TouchEvent) {
  ev.preventDefault();
  posFromEvent(ev);
  const move = (e: MouseEvent | TouchEvent) => posFromEvent(e);
  const up = () => {
    window.removeEventListener("mousemove", move);
    window.removeEventListener("mouseup", up);
    window.removeEventListener("touchmove", move);
    window.removeEventListener("touchend", up);
  };
  window.addEventListener("mousemove", move);
  window.addEventListener("mouseup", up);
  window.addEventListener("touchmove", move, { passive: false });
  window.addEventListener("touchend", up);
}
</script>

<template>
  <div>
    <div
      ref="sv"
      class="sv-box"
      :style="{ background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, hsl(${hsv.h}, 100%, 50%))` }"
      @mousedown="startSv"
      @touchstart.prevent="startSv"
    >
      <div class="sv-thumb" :style="{ left: hsv.s + '%', top: 100 - hsv.v + '%', background: hex }" />
    </div>
    <input
      class="hue-slider"
      type="range"
      min="0"
      max="360"
      step="0.5"
      :value="hsv.h"
      @input="setHsv({ h: Number(($event.target as HTMLInputElement).value) })"
    />
    <div class="kvs" style="margin-top:12px">
      <div>
        <label class="lbl">Hex</label>
        <input v-model="hexInput" class="mono" />
      </div>
      <div>
        <label class="lbl">Hue</label>
        <input type="number" :value="Math.round(hsv.h)" min="0" max="360" @change="setHsv({ h: Number(($event.target as HTMLInputElement).value) })" />
      </div>
      <div>
        <label class="lbl">S %</label>
        <input type="number" :value="Math.round(hsv.s)" min="0" max="100" @change="setHsv({ s: Number(($event.target as HTMLInputElement).value) })" />
      </div>
      <div>
        <label class="lbl">V %</label>
        <input type="number" :value="Math.round(hsv.v)" min="0" max="100" @change="setHsv({ v: Number(($event.target as HTMLInputElement).value) })" />
      </div>
      <div>
        <label class="lbl">R</label>
        <input type="number" :value="rgb.r" min="0" max="255" @change="setRgb({ r: Number(($event.target as HTMLInputElement).value) })" />
      </div>
      <div>
        <label class="lbl">G</label>
        <input type="number" :value="rgb.g" min="0" max="255" @change="setRgb({ g: Number(($event.target as HTMLInputElement).value) })" />
      </div>
      <div>
        <label class="lbl">B</label>
        <input type="number" :value="rgb.b" min="0" max="255" @change="setRgb({ b: Number(($event.target as HTMLInputElement).value) })" />
      </div>
    </div>
  </div>
</template>
