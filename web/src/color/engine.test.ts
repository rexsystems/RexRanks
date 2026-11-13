import { describe, expect, it } from "vitest";
import {
  colorText,
  evenly,
  gradientLegacy,
  gradientMiniMessage,
  interpolate,
  normalizeHex,
  parseColorInput,
  parseHex,
  preciseMiniMessage,
  rainbowLegacy,
  rainbowMiniMessage,
  rgbToHsv,
  hsvToRgb,
  solidLegacy,
  solidMiniMessage,
  toHex,
  wrapDecorations,
} from "./engine";

describe("hex", () => {
  it("normalises hashes and case", () => {
    expect(normalizeHex("FF00aa")).toBe("#ff00aa");
    expect(normalizeHex("#ABCDEFaa".slice(0, 7))).toBe("#abcdef");
  });
  it("rejects junk", () => {
    expect(() => normalizeHex("red")).toThrow();
  });
  it("round-trips rgb", () => {
    const rgb = parseHex("#e8b84a");
    expect(toHex(rgb)).toBe("#e8b84a");
  });
});

describe("hsv", () => {
  it("round-trips primary colours", () => {
    for (const hex of ["#ff0000", "#00ff00", "#0000ff", "#ffffff", "#000000", "#e8b84a"]) {
      const back = toHex(hsvToRgb(rgbToHsv(parseHex(hex))));
      const a = parseHex(hex);
      const b = parseHex(back);
      expect(Math.abs(a.r - b.r)).toBeLessThanOrEqual(1);
      expect(Math.abs(a.g - b.g)).toBeLessThanOrEqual(1);
      expect(Math.abs(a.b - b.b)).toBeLessThanOrEqual(1);
    }
  });
});

describe("gradients", () => {
  it("interpolates the midpoint of a two-stop RGB gradient", () => {
    const stops = evenly(["#000000", "#ffffff"]);
    const mid = interpolate(stops, 0.5);
    expect(mid.r).toBe(128);
    expect(mid.g).toBe(128);
    expect(mid.b).toBe(128);
  });
  it("builds MiniMessage tags", () => {
    expect(solidMiniMessage("#ff0000", "Hi")).toBe("<#ff0000>Hi</#ff0000>");
    expect(gradientMiniMessage(["#ff0000", "#00ff00"], "Hi")).toBe(
      "<gradient:#ff0000:#00ff00>Hi</gradient>",
    );
    expect(rainbowMiniMessage("Hi", true)).toBe("<rainbow:!>Hi</rainbow>");
    expect(solidLegacy("#aabbcc", "Z")).toBe("&#aabbccZ");
  });
  it("skips whitespace in legacy expansion", () => {
    const out = gradientLegacy(evenly(["#ff0000", "#0000ff"]), "A B");
    expect(out.startsWith("&#")).toBe(true);
    expect(out).toContain(" ");
    expect(out.match(/&#/g)?.length).toBe(2);
  });
  it("matches Java rainbow hue direction", () => {
    const fwd = rainbowLegacy("ABCD", false, 100, 100, 0);
    const back = rainbowLegacy("ABCD", true, 100, 100, 0);
    expect(fwd).not.toBe(back);
    expect(fwd.startsWith("&#")).toBe(true);
  });
  it("emits per-character MiniMessage", () => {
    const chars = colorText("AB", evenly(["#ff0000", "#00ff00"]));
    const mm = preciseMiniMessage(chars);
    expect(mm).toContain("<#");
    expect(mm).toContain("A");
    expect(mm).toContain("B");
  });
});

describe("parse", () => {
  it("parses a MiniMessage gradient", () => {
    const p = parseColorInput("<gradient:#ff0000:#00ff00>Staff</gradient>");
    expect(p?.mode).toBe("gradient");
    expect(p?.text).toBe("Staff");
    expect(p?.stops).toHaveLength(2);
  });
  it("parses decorations and rainbow", () => {
    const p = parseColorInput("<bold><rainbow:!>VIP</rainbow></bold>");
    expect(p?.mode).toBe("rainbow");
    expect(p?.backwards).toBe(true);
    expect(p?.decorations.bold).toBe(true);
    expect(p?.text).toBe("VIP");
  });
  it("parses a raw hex", () => {
    expect(parseColorInput("#e8b84a")?.mode).toBe("solid");
  });
});

describe("decorations", () => {
  it("wraps MiniMessage in the documented order", () => {
    const s = wrapDecorations("X", {
      bold: true,
      italic: true,
      underlined: false,
      strikethrough: false,
      obfuscated: false,
    });
    expect(s).toBe("<bold><italic>X</italic></bold>");
  });
});
