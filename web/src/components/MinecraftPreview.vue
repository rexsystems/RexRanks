<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { cssDecorations, type ColoredChar, type Decorations } from "../color/engine";

const props = defineProps<{
  chars: ColoredChar[];
  decorations: Decorations;
  variant: "chat" | "tab" | "nametag";
  username?: string;
}>();

const tick = ref(0);
let timer: number | undefined;
const glyphs = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#*+";

onMounted(() => {
  timer = window.setInterval(() => {
    if (props.decorations.obfuscated) tick.value++;
  }, 50);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

function display(c: ColoredChar, i: number): string {
  if (!props.decorations.obfuscated || !c.visible) return c.char;
  const idx = (i * 7 + tick.value * 3) % glyphs.length;
  return glyphs[idx]!;
}

const style = computed(() => cssDecorations(props.decorations));
const user = computed(() => props.username || "Steve");
</script>

<template>
  <div v-if="variant === 'chat'" class="mc-chat" :style="style">
    <span
      v-for="(c, i) in chars"
      :key="i"
      class="mc-char"
      :style="{ color: c.visible ? c.hex : 'transparent' }"
    >{{ display(c, i) }}</span>
    <span style="color:#ffffff">{{ user }}</span>
    <span style="color:#aaaaaa">: hey, check this out</span>
  </div>
  <div v-else-if="variant === 'tab'" class="mc-tab" :style="style">
    <div style="color:#aaaaaa;font-size:11px;letter-spacing:0.14em;text-transform:uppercase;margin-bottom:6px;text-shadow:none">Players</div>
    <div>
      <span v-for="(c, i) in chars" :key="i" :style="{ color: c.visible ? c.hex : 'transparent', textShadow: '2px 2px 0 rgba(0,0,0,0.85)', fontFamily: 'var(--mono)', fontSize: '16px' }">{{ display(c, i) }}</span>
      <span style="color:#ffffff;text-shadow:2px 2px 0 rgba(0,0,0,0.85);font-family:var(--mono);font-size:16px">{{ user }}</span>
    </div>
    <div style="color:#888;font-size:12px;margin-top:4px;text-shadow:none">1/120</div>
  </div>
  <div v-else class="mc-nameplate" :style="style">
    <span v-for="(c, i) in chars" :key="'n'+i" :style="{ color: c.visible ? c.hex : 'transparent' }">{{ display(c, i) }}</span>
    <span style="color:#ffffff">{{ user }}</span>
  </div>
</template>
