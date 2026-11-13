import type { GradientStop } from "./engine";
import { evenly, newId } from "./engine";

export interface Preset {
  id: string;
  name: string;
  group: "warm" | "cool" | "neon" | "natural" | "mono";
  stops: GradientStop[];
}

function p(id: string, name: string, group: Preset["group"], hexes: string[]): Preset {
  return { id, name, group, stops: evenly(hexes) };
}

export const PRESETS: Preset[] = [
  p("sunset", "Sunset", "warm", ["#ff5e62", "#ff9966", "#ffc371"]),
  p("ember", "Ember", "warm", ["#3b0a0a", "#c23a2b", "#ff9a3c", "#ffe29a"]),
  p("lava", "Lava", "warm", ["#1a0000", "#ff1e00", "#ffb347", "#fff3a0"]),
  p("gold", "Royal gold", "warm", ["#8a5a12", "#e8b84a", "#fff1b8", "#c48a1a"]),
  p("rose", "Rose", "warm", ["#ffafbd", "#ffc3a0"]),
  p("cherry", "Cherry", "warm", ["#7b1e3a", "#e63956", "#ffb3c6"]),
  p("ocean", "Ocean", "cool", ["#0b1320", "#1c6e8c", "#4cc9f0", "#caf0f8"]),
  p("ice", "Ice", "cool", ["#a8dadc", "#f1faee", "#457b9d"]),
  p("aurora", "Aurora", "cool", ["#5effc3", "#7ae1ff", "#c77dff", "#5effc3"]),
  p("forest", "Forest", "natural", ["#0b3d2e", "#2d6a4f", "#95d5b2", "#d8f3dc"]),
  p("toxic", "Toxic", "neon", ["#0d0d0d", "#39ff14", "#c8ff00"]),
  p("neon", "Neon night", "neon", ["#ff006e", "#8338ec", "#3a86ff"]),
  p("vapor", "Vaporwave", "neon", ["#ff71ce", "#01cdfe", "#05ffa1", "#b967ff"]),
  p("candy", "Candy", "neon", ["#ff6ad5", "#c774e8", "#ad8cff", "#8795e8", "#94d0ff"]),
  p("midnight", "Midnight", "cool", ["#0f0c29", "#302b63", "#24243e"]),
  p("cotton", "Cotton candy", "warm", ["#fbc2eb", "#a6c1ee"]),
  p("mono", "Porcelain", "mono", ["#f8f4ec", "#cfc6b8", "#6f675c"]),
  p("ink", "Ink", "mono", ["#0c0d10", "#4a4d55", "#e8e4da"]),
  p("blood", "Crimson", "warm", ["#3d0000", "#9b0000", "#ff4d4d"]),
  p("honey", "Honey", "warm", ["#f6d365", "#fda085"]),
  p("mint", "Mint", "natural", ["#d4fc79", "#96e6a1"]),
  p("royal", "Royal", "cool", ["#141e30", "#4a00e0", "#8e2de2"]),
  p("copper", "Copper", "warm", ["#3d1c08", "#b87333", "#f6d5a8"]),
  p("twilight", "Twilight", "cool", ["#0f2027", "#203a43", "#f7976c"]),
];

export function cloneStops(stops: GradientStop[]): GradientStop[] {
  return stops.map((s) => ({ ...s, id: newId() }));
}
