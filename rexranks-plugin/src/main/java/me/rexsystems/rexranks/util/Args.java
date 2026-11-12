package me.rexsystems.rexranks.util;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.engine.DurationParser;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Args {
    private final String[] raw;
    private int index;

    public Args(String[] raw) {
        this.raw = raw == null ? new String[0] : raw;
    }

    public boolean hasNext() {
        return index < raw.length;
    }

    public int remaining() {
        return raw.length - index;
    }

    public int index() {
        return index;
    }

    public String peek() {
        return hasNext() ? raw[index] : null;
    }

    public String next() {
        return hasNext() ? raw[index++] : null;
    }

    public String nextOr(String fallback) {
        return hasNext() ? raw[index++] : fallback;
    }

    public String rest() {
        if (!hasNext()) {
            return "";
        }
        String joined = String.join(" ", Arrays.copyOfRange(raw, index, raw.length));
        index = raw.length;
        return joined;
    }

    public String restQuoted() {
        return rest();
    }

    public Optional<Boolean> nextBoolean() {
        if (!hasNext()) {
            return Optional.empty();
        }
        String value = peek().toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("yes") || value.equals("on") || value.equals("allow")) {
            index++;
            return Optional.of(true);
        }
        if (value.equals("false") || value.equals("no") || value.equals("off") || value.equals("deny")) {
            index++;
            return Optional.of(false);
        }
        return Optional.empty();
    }

    public Optional<Duration> nextDuration() {
        if (!hasNext()) {
            return Optional.empty();
        }
        // durations can be a single token (1d) or "7 days"
        String first = peek();
        Optional<Duration> parsed = DurationParser.parse(first);
        if (parsed.isPresent()) {
            index++;
            return parsed;
        }
        if (remaining() >= 2) {
            parsed = DurationParser.parse(raw[index] + raw[index + 1]);
            if (parsed.isPresent()) {
                index += 2;
                return parsed;
            }
        }
        return Optional.empty();
    }

    public Optional<Instant> nextExpiry(ZoneId zone) {
        if (!hasNext()) {
            return Optional.empty();
        }
        Optional<Instant> instant = DurationParser.parseExpiry(peek(), zone);
        if (instant.isPresent()) {
            index++;
            return instant;
        }
        if (remaining() >= 2) {
            instant = DurationParser.parseExpiry(raw[index] + " " + raw[index + 1], zone);
            if (instant.isPresent()) {
                index += 2;
                return instant;
            }
        }
        return Optional.empty();
    }

    /**
     * Consumes remaining {@code key=value} pairs as contexts. Stops at the first
     * token that is not a pair.
     */
    public ImmutableContextSet remainingContexts() {
        ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
        while (hasNext()) {
            String token = peek();
            int eq = token.indexOf('=');
            if (eq <= 0 || eq == token.length() - 1) {
                break;
            }
            builder.add(token.substring(0, eq), token.substring(eq + 1));
            index++;
        }
        return builder.build();
    }

    public List<String> tail() {
        if (!hasNext()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(raw, index, raw.length)));
        index = raw.length;
        return list;
    }

    public String[] raw() {
        return raw;
    }
}
