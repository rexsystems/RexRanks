<script setup lang="ts">
import { computed, ref } from "vue";
import { contrastText, cssGradient, newId, type Easing, type GradientStop } from "../color/engine";

const props = defineProps<{
  stops: GradientStop[];
  selectedId: string | null;
  easing?: Easing;
}>();
const emit = defineEmits<{
  (e: "update:stops", stops: GradientStop[]): void;
  (e: "update:selectedId", id: string | null): void;
}>();

const bar = ref<HTMLElement | null>(null);
const bg = computed(() => cssGradient(props.stops, props.easing || "linear"));

function xToPos(clientX: number): number {
  const el = bar.value;
  if (!el) return 0;
  const r = el.getBoundingClientRect();
  return Math.max(0, Math.min(1, (clientX - r.left) / r.width));
}

function onBarClick(ev: MouseEvent) {
  if ((ev.target as HTMLElement).closest(".grad-stop")) return;
  const pos = xToPos(ev.clientX);
  // Colour from existing gradient at that position — caller can refine.
  const id = newId();
  // Pick nearest colour as a starting point
  const sorted = [...props.stops].sort((a, b) => a.position - b.position);
  let hex = sorted[0]?.hex ?? "#ffffff";
  for (const s of sorted) {
    if (s.position <= pos) hex = s.hex;
  }
  const next = [...props.stops, { id, position: pos, hex, space: "rgb" as const }];
  emit("update:stops", next);
  emit("update:selectedId", id);
}

function startDrag(ev: MouseEvent | TouchEvent, id: string) {
  ev.preventDefault();
  ev.stopPropagation();
  emit("update:selectedId", id);
  const move = (e: MouseEvent | TouchEvent) => {
    const pt = "touches" in e ? e.touches[0] : e;
    if (!pt) return;
    const pos = xToPos(pt.clientX);
    emit(
      "update:stops",
      props.stops.map((s) => (s.id === id ? { ...s, position: pos } : s)),
    );
  };
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

function onKey(ev: KeyboardEvent, id: string) {
  if (ev.key === "Delete" || ev.key === "Backspace") {
    if (props.stops.length <= 1) return;
    ev.preventDefault();
    const next = props.stops.filter((s) => s.id !== id);
    emit("update:stops", next);
    emit("update:selectedId", next[0]?.id ?? null);
  }
  if (ev.key === "ArrowLeft" || ev.key === "ArrowRight") {
    ev.preventDefault();
    const d = ev.key === "ArrowLeft" ? -0.01 : 0.01;
    emit(
      "update:stops",
      props.stops.map((s) => (s.id === id ? { ...s, position: Math.max(0, Math.min(1, s.position + d)) } : s)),
    );
  }
}
</script>

<template>
  <div class="grad-bar-wrap">
    <div ref="bar" class="grad-bar" :style="{ background: bg }" @mousedown="onBarClick" />
    <div
      v-for="s in stops"
      :key="s.id"
      class="grad-stop"
      :class="{ active: s.id === selectedId }"
      :style="{ left: s.position * 100 + '%' }"
      tabindex="0"
      @mousedown="startDrag($event, s.id)"
      @touchstart.prevent="startDrag($event, s.id)"
      @keydown="onKey($event, s.id)"
      @click.stop="emit('update:selectedId', s.id)"
      :title="s.hex + ' · ' + Math.round(s.position * 100) + '%'"
    >
      <div class="knob" :style="{ background: s.hex, color: contrastText(s.hex) }" />
      <div class="stem" />
    </div>
  </div>
</template>
