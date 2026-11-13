/** Colour math shared by the web studio. Mirrors the Java ColorFormatter. */

export type ColorSpace = "rgb" | "hsv";
export type Easing = "linear" | "ease-in" | "ease-out" | "ease-in-out" | "sine";
export type StudioMode = "solid" | "gradient" | "rainbow";

export interface Rgb {
  r: number;
  g: number;
  b: number;
}

export interface Hsv {
  h: number; // 0–360
  s: number; // 0–100
  v: number; // 0–100
}

export interface GradientStop {
  id: string;
  position: number; // 0–1
  hex: string;
  space: ColorSpace;
}

export interface Decorations {
  bold: boolean;
  italic: boolean;
  underlined: boolean;
  strikethrough: boolean;
  obfuscated: boolean;
}

export interface ColoredChar {
  char: string;
  hex: string;
  visible: boolean;
}

const HEX = /#?([0-9a-f]{6})/i;

export function clamp01(t: number): number {
  return Math.max(0, Math.min(1, t));
}

export function clamp255(v: number): number {
  return Math.max(0, Math.min(255, Math.round(v)));
}

export function newId(): string {
  return Math.random().toString(36).slice(2, 10);
}

export function normalizeHex(hex: string): string {
  const m = HEX.exec(hex.trim());
  if (!m) throw new Error("Invalid hex colour: " + hex);
  return "#" + m[1]!.toLowerCase();
}

export function tryHex(hex: string): string | null {
  try {
    return normalizeHex(hex);
  } catch {
    return null;
  }
}

export function parseHex(hex: string): Rgb {
  const n = normalizeHex(hex).slice(1);
  return {
    r: parseInt(n.slice(0, 2), 16),
    g: parseInt(n.slice(2, 4), 16),
    b: parseInt(n.slice(4, 6), 16),
  };
}

export function toHex(rgb: Rgb): string {
  return (
    "#" +
    [rgb.r, rgb.g, rgb.b]
      .map((v) => clamp255(v).toString(16).padStart(2, "0"))
      .join("")
  );
}

export function rgbToHsv(rgb: Rgb): Hsv {
  const r = rgb.r / 255;
  const g = rgb.g / 255;
  const b = rgb.b / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  const s = max === 0 ? 0 : (d / max) * 100;
  return { h, s, v: max * 100 };
}

export function hsvToRgb(hsv: Hsv): Rgb {
  const h = ((hsv.h % 360) + 360) % 360;
  const s = clamp01(hsv.s / 100);
  const v = clamp01(hsv.v / 100);
  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;
  let r = 0,
    g = 0,
    b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  return {
    r: clamp255((r + m) * 255),
    g: clamp255((g + m) * 255),
    b: clamp255((b + m) * 255),
  };
}

export function lerpRgb(a: Rgb, b: Rgb, t: number): Rgb {
  const u = clamp01(t);
  return {
    r: clamp255(a.r + (b.r - a.r) * u),
    g: clamp255(a.g + (b.g - a.g) * u),
    b: clamp255(a.b + (b.b - a.b) * u),
  };
}

export function lerpHsv(a: Rgb, b: Rgb, t: number): Rgb {
  const u = clamp01(t);
  const ha = rgbToHsv(a);
  const hb = rgbToHsv(b);
  let dh = hb.h - ha.h;
  if (dh > 180) dh -= 360;
  else if (dh < -180) dh += 360;
  let h = ha.h + dh * u;
  if (h < 0) h += 360;
  if (h >= 360) h -= 360;
  return hsvToRgb({
    h,
    s: ha.s + (hb.s - ha.s) * u,
    v: ha.v + (hb.v - ha.v) * u,
  });
}

export function ease(t: number, kind: Easing): number {
  const x = clamp01(t);
  switch (kind) {
    case "ease-in":
      return x * x;
    case "ease-out":
      return 1 - (1 - x) * (1 - x);
    case "ease-in-out":
      return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
    case "sine":
      return 1 - Math.cos((x * Math.PI) / 2);
    default:
      return x;
  }
}

function normalizeStops(stops: GradientStop[]): GradientStop[] {
  const copy = [...stops].sort((a, b) => a.position - b.position);
  if (copy.length === 0) return copy;
  if (copy[0]!.position > 0) {
    copy.unshift({ ...copy[0]!, id: "edge-start", position: 0 });
  }
  if (copy[copy.length - 1]!.position < 1) {
    copy.push({ ...copy[copy.length - 1]!, id: "edge-end", position: 1 });
  }
  return copy;
}

export function interpolate(stops: GradientStop[], t: number, easing: Easing = "linear"): Rgb {
  const sorted = normalizeStops(stops);
  if (sorted.length === 0) return { r: 255, g: 255, b: 255 };
  if (sorted.length === 1) return parseHex(sorted[0]!.hex);
  const u = ease(clamp01(t), easing);
  let left = sorted[0]!;
  let right = sorted[sorted.length - 1]!;
  for (let i = 0; i < sorted.length - 1; i++) {
    const a = sorted[i]!;
    const b = sorted[i + 1]!;
    if (u >= a.position && u <= b.position) {
      left = a;
      right = b;
      break;
    }
  }
  const span = right.position - left.position;
  const local = span <= 0 ? 0 : (u - left.position) / span;
  const ca = parseHex(left.hex);
  const cb = parseHex(right.hex);
  if (left.space === "hsv" || right.space === "hsv") return lerpHsv(ca, cb, local);
  return lerpRgb(ca, cb, local);
}

export function sampleGradient(stops: GradientStop[], samples: number, easing: Easing = "linear"): string[] {
  if (samples <= 0) return [];
  if (stops.length === 0) return [];
  if (samples === 1) return [normalizeHex(stops[0]!.hex)];
  const out: string[] = [];
  for (let i = 0; i < samples; i++) {
    out.push(toHex(interpolate(stops, i / (samples - 1), easing)));
  }
  return out;
}

function isWhitespace(ch: string): boolean {
  return /\s/.test(ch);
}

function visibleLength(text: string): number {
  let n = 0;
  for (const ch of text) if (!isWhitespace(ch)) n++;
  return n;
}

export function colorText(
  text: string,
  stops: GradientStop[],
  easing: Easing = "linear",
): ColoredChar[] {
  const visible = Math.max(1, visibleLength(text));
  const out: ColoredChar[] = [];
  let vi = 0;
  for (const char of text) {
    if (isWhitespace(char)) {
      out.push({ char, hex: "#ffffff", visible: false });
    } else {
      const t = visible === 1 ? 0 : vi / (visible - 1);
      out.push({ char, hex: toHex(interpolate(stops, t, easing)), visible: true });
      vi++;
    }
  }
  return out;
}

export function rainbowText(
  text: string,
  backwards = false,
  saturation = 100,
  brightness = 100,
  phase = 0,
): ColoredChar[] {
  const visible = Math.max(1, visibleLength(text));
  const out: ColoredChar[] = [];
  let vi = 0;
  for (const char of text) {
    if (isWhitespace(char)) {
      out.push({ char, hex: "#ffffff", visible: false });
    } else {
      let hue = visible === 1 ? 0 : vi / visible;
      hue = (hue + phase) % 1;
      if (hue < 0) hue += 1;
      if (backwards) hue = 1 - hue;
      const rgb = hsvToRgb({ h: hue * 360, s: saturation, v: brightness });
      out.push({ char, hex: toHex(rgb), visible: true });
      vi++;
    }
  }
  return out;
}

export function solidMiniMessage(hex: string, text: string): string {
  const h = normalizeHex(hex);
  return `<${h}>${text}</${h}>`;
}

export function solidLegacy(hex: string, text: string): string {
  return "&#" + normalizeHex(hex).slice(1) + text;
}

export function gradientMiniMessage(hexStops: string[], text: string): string {
  if (!hexStops.length) return text;
  if (hexStops.length === 1) return solidMiniMessage(hexStops[0]!, text);
  return `<gradient:${hexStops.map((h) => normalizeHex(h)).join(":")}>${text}</gradient>`;
}

export function gradientLegacy(stops: GradientStop[], text: string, easing: Easing = "linear"): string {
  return colorText(text, stops, easing)
    .map((c) => (c.visible ? "&#" + c.hex.slice(1) + c.char : c.char))
    .join("");
}

/** Per-character MiniMessage that honours stop positions and easing. */
export function preciseMiniMessage(chars: ColoredChar[]): string {
  return chars
    .map((c) => (c.visible ? `<${c.hex}>${escapeMm(c.char)}</${c.hex}>` : c.char))
    .join("");
}

export function rainbowMiniMessage(text: string, backwards = false): string {
  return backwards ? `<rainbow:!>${text}</rainbow>` : `<rainbow>${text}</rainbow>`;
}

export function rainbowLegacy(
  text: string,
  backwards = false,
  saturation = 100,
  brightness = 100,
  phase = 0,
): string {
  return rainbowText(text, backwards, saturation, brightness, phase)
    .map((c) => (c.visible ? "&#" + c.hex.slice(1) + c.char : c.char))
    .join("");
}

function escapeMm(ch: string): string {
  if (ch === "<") return "\\<";
  if (ch === ">") return "\\>";
  return ch;
}

export function wrapDecorations(inner: string, deco: Decorations): string {
  let s = inner;
  if (deco.obfuscated) s = `<obfuscated>${s}</obfuscated>`;
  if (deco.strikethrough) s = `<strikethrough>${s}</strikethrough>`;
  if (deco.underlined) s = `<underlined>${s}</underlined>`;
  if (deco.italic) s = `<italic>${s}</italic>`;
  if (deco.bold) s = `<bold>${s}</bold>`;
  return s;
}

export function cssDecorations(deco: Decorations): string {
  const parts: string[] = [];
  if (deco.bold) parts.push("font-weight:700");
  if (deco.italic) parts.push("font-style:italic");
  const td: string[] = [];
  if (deco.underlined) td.push("underline");
  if (deco.strikethrough) td.push("line-through");
  if (td.length) parts.push("text-decoration:" + td.join(" "));
  return parts.join(";");
}

export interface ParsedColor {
  mode: StudioMode;
  stops: GradientStop[];
  text: string;
  backwards: boolean;
  decorations: Decorations;
}

const EMPTY_DECO: Decorations = {
  bold: false,
  italic: false,
  underlined: false,
  strikethrough: false,
  obfuscated: false,
};

/** Best-effort parse of MiniMessage / legacy hex into a studio session. */
export function parseColorInput(raw: string): ParsedColor | null {
  const input = raw.trim();
  if (!input) return null;
  const deco: Decorations = { ...EMPTY_DECO };
  let body = input;
  const wrap = (tag: keyof Decorations, names: string[]) => {
    for (const n of names) {
      const re = new RegExp(`^<${n}>([\\s\\S]*)<\\/${n}>$`, "i");
      const m = re.exec(body);
      if (m) {
        deco[tag] = true;
        body = m[1]!;
      }
    }
  };
  wrap("bold", ["bold", "b"]);
  wrap("italic", ["italic", "i", "em"]);
  wrap("underlined", ["underlined", "u"]);
  wrap("strikethrough", ["strikethrough", "st"]);
  wrap("obfuscated", ["obfuscated", "obf"]);

  const rainbow = /^<rainbow(?::(!))?>((?:.|\n)*)<\/rainbow>$/i.exec(body);
  if (rainbow) {
    return {
      mode: "rainbow",
      stops: defaultStops(),
      text: rainbow[2] ?? "",
      backwards: !!rainbow[1],
      decorations: deco,
    };
  }
  const gradient = /^<gradient:([^>]+)>((?:.|\n)*)<\/gradient>$/i.exec(body);
  if (gradient) {
    const hexes = gradient[1]!.split(":").map((s) => tryHex(s)).filter((s): s is string => !!s);
    if (hexes.length >= 2) {
      return {
        mode: "gradient",
        stops: evenly(hexes),
        text: gradient[2] ?? "",
        backwards: false,
        decorations: deco,
      };
    }
  }
  const solid = /^<#([0-9a-f]{6})>((?:.|\n)*)<\/#\1>$/i.exec(body);
  if (solid) {
    return {
      mode: "solid",
      stops: evenly(["#" + solid[1]!.toLowerCase()]),
      text: solid[2] ?? "",
      backwards: false,
      decorations: deco,
    };
  }
  if (/^&#[0-9a-f]{6}/i.test(body)) {
    const chars: { hex: string; ch: string }[] = [];
    const re = /&#([0-9a-f]{6})(?:§[0-9a-fk-or])*([^\s&#]| )?/gi;
    let m: RegExpExecArray | null;
    let text = "";
    const hexes: string[] = [];
    while ((m = re.exec(body))) {
      const hex = "#" + m[1]!.toLowerCase();
      const ch = m[2] ?? "";
      chars.push({ hex, ch });
      text += ch;
      hexes.push(hex);
    }
    if (hexes.length === 0) return null;
    const unique = uniqueInOrder(hexes);
    if (unique.length === 1) {
      return { mode: "solid", stops: evenly(unique), text, backwards: false, decorations: deco };
    }
    // Sample a few stops from the per-character sequence.
    const picks = pickStops(chars.filter((c) => c.ch.trim()));
    return { mode: "gradient", stops: picks, text, backwards: false, decorations: deco };
  }
  const only = tryHex(body);
  if (only) {
    return { mode: "solid", stops: evenly([only]), text: "RexRanks", backwards: false, decorations: deco };
  }
  return null;
}

export function evenly(hexes: string[]): GradientStop[] {
  if (hexes.length === 0) return defaultStops();
  if (hexes.length === 1) {
    return [{ id: newId(), position: 0, hex: normalizeHex(hexes[0]!), space: "rgb" }];
  }
  return hexes.map((hex, i) => ({
    id: newId(),
    position: i / (hexes.length - 1),
    hex: normalizeHex(hex),
    space: "rgb" as ColorSpace,
  }));
}

export function defaultStops(): GradientStop[] {
  return evenly(["#ff6b6b", "#ffd36a", "#6bcbff"]);
}

function uniqueInOrder(hexes: string[]): string[] {
  const out: string[] = [];
  for (const h of hexes) if (out[out.length - 1] !== h) out.push(h);
  return out;
}

function pickStops(chars: { hex: string; ch: string }[]): GradientStop[] {
  if (chars.length === 0) return defaultStops();
  const n = Math.min(6, Math.max(2, Math.round(chars.length / 4) + 1));
  const hexes: string[] = [];
  for (let i = 0; i < n; i++) {
    const idx = Math.round((i / (n - 1)) * (chars.length - 1));
    hexes.push(chars[idx]!.hex);
  }
  return evenly(uniqueInOrder(hexes).length >= 2 ? uniqueInOrder(hexes) : hexes);
}

export function distributeStops(stops: GradientStop[]): GradientStop[] {
  if (stops.length <= 1) return stops.map((s) => ({ ...s, position: 0 }));
  return stops.map((s, i) => ({ ...s, position: i / (stops.length - 1) }));
}

export function reverseStops(stops: GradientStop[]): GradientStop[] {
  return stops.map((s, i, arr) => ({
    ...s,
    hex: arr[arr.length - 1 - i]!.hex,
    space: arr[arr.length - 1 - i]!.space,
  }));
}

export function cssGradient(stops: GradientStop[], easing: Easing = "linear"): string {
  if (stops.length === 0) return "#888";
  if (stops.length === 1) return normalizeHex(stops[0]!.hex);
  // CSS linear-gradient cannot honour HSV / easing exactly; sample densely.
  const samples = sampleGradient(stops, 24, easing);
  return `linear-gradient(90deg, ${samples
    .map((hex, i) => `${hex} ${(i / (samples.length - 1)) * 100}%`)
    .join(", ")})`;
}

export function contrastText(hex: string): string {
  const { r, g, b } = parseHex(hex);
  const y = (r * 299 + g * 587 + b * 114) / 1000;
  return y > 150 ? "#1a1208" : "#fff8ea";
}

export function randomHex(): string {
  return toHex({
    r: Math.floor(Math.random() * 256),
    g: Math.floor(Math.random() * 256),
    b: Math.floor(Math.random() * 256),
  });
}

export function harmonies(hex: string): Record<string, string[]> {
  const hsv = rgbToHsv(parseHex(hex));
  const spin = (deg: number) => toHex(hsvToRgb({ ...hsv, h: (hsv.h + deg + 360) % 360 }));
  return {
    complementary: [hex, spin(180)],
    analogous: [spin(-30), hex, spin(30)],
    triadic: [hex, spin(120), spin(240)],
    split: [hex, spin(150), spin(210)],
    tetradic: [hex, spin(90), spin(180), spin(270)],
  };
}
