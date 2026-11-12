package me.rexsystems.rexranks.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {
    @Test
    void parsesCombinedTokens() {
        Duration d = DurationParser.parse("1w2d3h4m5s").orElseThrow();
        assertEquals(Duration.ofDays(9).plusHours(3).plusMinutes(4).plusSeconds(5), d);
    }

    @Test
    void parsesWords() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("7 days").orElseThrow());
        assertEquals(Duration.ofHours(2), DurationParser.parse("2hours").orElseThrow());
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m").orElseThrow());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(DurationParser.parse("tomorrow").isEmpty());
        assertTrue(DurationParser.parse("").isEmpty());
        assertTrue(DurationParser.parse("0s").isEmpty());
    }

    @Test
    void formatsRoundTrip() {
        Duration d = DurationParser.parse("2d5h").orElseThrow();
        assertEquals("2d5h", DurationParser.format(d));
    }
}
