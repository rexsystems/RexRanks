package me.rexsystems.rexranks.engine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds MiniMessage, legacy {@code &} and {@code &#RRGGBB} strings for solid
 * colours, multi-stop gradients, and rainbows. Used by the in-game colour
 * workshop and by the web editor's equivalent output.
 *
 * <p>This class is deliberately independent of Adventure so it can be unit-tested
 * without the Paper API.
 */
public final class ColorFormatter {
    public static final Pattern HEX = Pattern.compile("(?i)#?([0-9a-f]{6})");

    private ColorFormatter() {
    }

    public static String solidMiniMessage(String hex, String text) {
        return "<" + normalizeHex(hex) + ">" + text + "</" + normalizeHex(hex) + ">";
    }

    public static String solidLegacy(String hex, String text) {
        return "&#" + normalizeHex(hex).substring(1) + text;
    }

    /**
     * Multi-stop MiniMessage gradient. Stops are {@code #RRGGBB} and positions
     * are ignored by MiniMessage itself (evenly spaced); positions are used
     * when expanding to per-character legacy hex.
     */
    public static String gradientMiniMessage(List<String> hexStops, String text) {
        if (hexStops == null || hexStops.isEmpty()) {
            return text;
        }
        if (hexStops.size() == 1) {
            return solidMiniMessage(hexStops.getFirst(), text);
        }
        StringBuilder sb = new StringBuilder("<gradient");
        for (String stop : hexStops) {
            sb.append(':').append(normalizeHex(stop));
        }
        sb.append('>').append(text).append("</gradient>");
        return sb.toString();
    }

    /**
     * Per-character {@code &#RRGGBB} gradient using stop positions in {@code [0, 1]}.
     */
    public static String gradientLegacy(List<GradientStop> stops, String text) {
        if (text.isEmpty()) {
            return "";
        }
        if (stops == null || stops.isEmpty()) {
            return text;
        }
        if (stops.size() == 1) {
            return solidLegacy(toHex(stops.getFirst().color()), text);
        }
        List<GradientStop> sorted = normalizeStops(stops);
        int visible = visibleLength(text);
        StringBuilder sb = new StringBuilder(text.length() * 10);
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
            } else {
                float t = visible == 1 ? 0f : (float) visibleIndex / (float) (visible - 1);
                Color color = interpolate(sorted, t);
                sb.append("&#").append(toHex(color).substring(1));
                sb.appendCodePoint(cp);
                visibleIndex++;
            }
            i += len;
        }
        return sb.toString();
    }

    public static String rainbowMiniMessage(String text, boolean backwards, boolean bold) {
        String tag = backwards ? "<rainbow:!>" : "<rainbow>";
        String close = "</rainbow>";
        if (bold) {
            return "<bold>" + tag + text + close + "</bold>";
        }
        return tag + text + close;
    }

    /**
     * Expand a rainbow to per-character hex, evenly rotating hue across visible characters.
     */
    public static String rainbowLegacy(String text, boolean backwards, float saturation, float brightness) {
        if (text.isEmpty()) {
            return "";
        }
        int visible = Math.max(1, visibleLength(text));
        StringBuilder sb = new StringBuilder(text.length() * 10);
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
            } else {
                float hue = visible == 1 ? 0f : (float) visibleIndex / (float) visible;
                if (backwards) {
                    hue = 1f - hue;
                }
                Color color = Color.getHSBColor(hue, saturation, brightness);
                sb.append("&#").append(toHex(color).substring(1));
                sb.appendCodePoint(cp);
                visibleIndex++;
            }
            i += len;
        }
        return sb.toString();
    }

    public static List<Color> sampleGradient(List<GradientStop> stops, int samples) {
        List<GradientStop> sorted = normalizeStops(stops);
        List<Color> out = new ArrayList<>(samples);
        if (samples <= 0) {
            return out;
        }
        if (sorted.isEmpty()) {
            return out;
        }
        if (samples == 1) {
            out.add(sorted.getFirst().color());
            return out;
        }
        for (int i = 0; i < samples; i++) {
            float t = (float) i / (float) (samples - 1);
            out.add(interpolate(sorted, t));
        }
        return out;
    }

    public static Color interpolate(List<GradientStop> sorted, float t) {
        if (sorted.size() == 1) {
            return sorted.getFirst().color();
        }
        t = clamp01(t);
        GradientStop left = sorted.getFirst();
        GradientStop right = sorted.getLast();
        for (int i = 0; i < sorted.size() - 1; i++) {
            GradientStop a = sorted.get(i);
            GradientStop b = sorted.get(i + 1);
            if (t >= a.position() && t <= b.position()) {
                left = a;
                right = b;
                break;
            }
        }
        float span = right.position() - left.position();
        float local = span <= 0 ? 0 : (t - left.position()) / span;
        if ("hsv".equalsIgnoreCase(left.space()) || "hsv".equalsIgnoreCase(right.space())) {
            return lerpHsv(left.color(), right.color(), local);
        }
        return lerpRgb(left.color(), right.color(), local);
    }

    public static Color lerpRgb(Color a, Color b, float t) {
        t = clamp01(t);
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(clamp255(r), clamp255(g), clamp255(bl));
    }

    public static Color lerpHsv(Color a, Color b, float t) {
        t = clamp01(t);
        float[] ha = Color.RGBtoHSB(a.getRed(), a.getGreen(), a.getBlue(), null);
        float[] hb = Color.RGBtoHSB(b.getRed(), b.getGreen(), b.getBlue(), null);
        float dh = hb[0] - ha[0];
        if (dh > 0.5f) {
            dh -= 1f;
        } else if (dh < -0.5f) {
            dh += 1f;
        }
        float h = ha[0] + dh * t;
        if (h < 0) {
            h += 1f;
        }
        if (h >= 1) {
            h -= 1f;
        }
        float s = ha[1] + (hb[1] - ha[1]) * t;
        float v = ha[2] + (hb[2] - ha[2]) * t;
        return Color.getHSBColor(h, s, v);
    }

    public static String normalizeHex(String hex) {
        Matcher matcher = HEX.matcher(hex.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid hex colour: " + hex);
        }
        return "#" + matcher.group(1).toLowerCase(Locale.ROOT);
    }

    public static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Color parseHex(String hex) {
        String n = normalizeHex(hex).substring(1);
        return new Color(Integer.parseInt(n, 16));
    }

    public static String rgb(int r, int g, int b) {
        return toHex(new Color(clamp255(r), clamp255(g), clamp255(b)));
    }

    public static Hsv toHsv(Color color) {
        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return new Hsv(hsv[0] * 360f, hsv[1] * 100f, hsv[2] * 100f);
    }

    public static Color fromHsv(float hueDeg, float satPct, float valPct) {
        float h = ((hueDeg % 360f) + 360f) % 360f / 360f;
        return Color.getHSBColor(h, clamp01(satPct / 100f), clamp01(valPct / 100f));
    }

    private static List<GradientStop> normalizeStops(List<GradientStop> stops) {
        List<GradientStop> copy = new ArrayList<>(stops);
        copy.sort((a, b) -> Float.compare(a.position(), b.position()));
        if (copy.getFirst().position() > 0f) {
            copy.addFirst(new GradientStop(0f, copy.getFirst().color(), copy.getFirst().space()));
        }
        if (copy.getLast().position() < 1f) {
            copy.add(new GradientStop(1f, copy.getLast().color(), copy.getLast().space()));
        }
        return copy;
    }

    private static int visibleLength(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                n++;
            }
            i += Character.charCount(cp);
        }
        return n;
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public record GradientStop(float position, Color color, String space) {
        public GradientStop(float position, Color color) {
            this(position, color, "rgb");
        }

        public GradientStop {
            position = Math.max(0f, Math.min(1f, position));
            if (space == null || space.isBlank()) {
                space = "rgb";
            }
        }
    }

    public record Hsv(float hue, float saturation, float value) {
    }
}
