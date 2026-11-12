package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.engine.ColorFormatter.GradientStop;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorFormatterTest {
    @Test
    void solidAndGradientMiniMessage() {
        assertEquals("<#ff0000>Hi</#ff0000>", ColorFormatter.solidMiniMessage("#FF0000", "Hi"));
        String mm = ColorFormatter.gradientMiniMessage(List.of("#ff0000", "#00ff00", "#0000ff"), "Hello");
        assertEquals("<gradient:#ff0000:#00ff00:#0000ff>Hello</gradient>", mm);
    }

    @Test
    void legacyGradientHasPerCharacterHex() {
        List<GradientStop> stops = List.of(
                new GradientStop(0f, Color.RED),
                new GradientStop(1f, Color.BLUE)
        );
        String out = ColorFormatter.gradientLegacy(stops, "AB");
        assertTrue(out.startsWith("&#"));
        assertTrue(out.contains("A"));
        assertTrue(out.contains("B"));
        assertTrue(out.length() > 4);
    }

    @Test
    void interpolateMidpointRgb() {
        List<GradientStop> stops = List.of(
                new GradientStop(0f, new Color(0, 0, 0)),
                new GradientStop(1f, new Color(100, 0, 0))
        );
        Color mid = ColorFormatter.interpolate(stops, 0.5f);
        assertEquals(50, mid.getRed());
        assertEquals(0, mid.getGreen());
    }

    @Test
    void rainbowAndHexRoundTrip() {
        assertEquals("#aabbcc", ColorFormatter.normalizeHex("AABBCC"));
        assertEquals("#aabbcc", ColorFormatter.toHex(ColorFormatter.parseHex("#AaBbCc")));
        String rainbow = ColorFormatter.rainbowMiniMessage("Hi", false, false);
        assertEquals("<rainbow>Hi</rainbow>", rainbow);
        String legacy = ColorFormatter.rainbowLegacy("AB", false, 1f, 1f);
        assertTrue(legacy.startsWith("&#") && legacy.contains("A") && legacy.contains("B"));
    }
}
