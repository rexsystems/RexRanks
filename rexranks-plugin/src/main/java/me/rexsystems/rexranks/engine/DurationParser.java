package me.rexsystems.rexranks.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human durations such as {@code 1w2d3h4m5s}, {@code 30m}, {@code 7 days},
 * and a few absolute date formats into a millisecond duration or expiry instant.
 */
public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile(
            "(\\d+)\\s*(w(?:eeks?)?|d(?:ays?)?|h(?:ours?)?|m(?:in(?:ute)?s?)?|s(?:ec(?:ond)?s?)?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter[] ABSOLUTE = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private DurationParser() {
    }

    public static Optional<Duration> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String raw = input.trim();
        Matcher matcher = TOKEN.matcher(raw);
        long totalSeconds = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed && !raw.substring(consumed, matcher.start()).isBlank()) {
                return Optional.empty();
            }
            long amount = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            totalSeconds += switch (unit) {
                case 'w' -> amount * 7L * 24 * 3600;
                case 'd' -> amount * 24L * 3600;
                case 'h' -> amount * 3600L;
                case 'm' -> amount * 60L;
                case 's' -> amount;
                default -> 0L;
            };
            consumed = matcher.end();
        }
        if (consumed == 0 || consumed < raw.length() && !raw.substring(consumed).isBlank()) {
            return Optional.empty();
        }
        if (totalSeconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(totalSeconds));
    }

    /**
     * Returns an expiry instant: either now + duration, or an absolute timestamp.
     */
    public static Optional<Instant> parseExpiry(String input, ZoneId zone) {
        Optional<Duration> duration = parse(input);
        if (duration.isPresent()) {
            return Optional.of(Instant.now().plus(duration.get()));
        }
        String raw = input.trim().replace('_', ' ');
        for (DateTimeFormatter formatter : ABSOLUTE) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(raw, formatter);
                return Optional.of(dateTime.atZone(zone).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            long epoch = Long.parseLong(raw);
            if (raw.length() <= 10) {
                epoch *= 1000;
            }
            return Optional.of(Instant.ofEpochMilli(epoch));
        } catch (NumberFormatException ignored) {
        }
        return Optional.empty();
    }

    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long weeks = seconds / (7 * 24 * 3600);
        seconds %= 7 * 24 * 3600;
        long days = seconds / (24 * 3600);
        seconds %= 24 * 3600;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (weeks > 0) {
            sb.append(weeks).append('w');
        }
        if (days > 0) {
            sb.append(days).append('d');
        }
        if (hours > 0) {
            sb.append(hours).append('h');
        }
        if (minutes > 0) {
            sb.append(minutes).append('m');
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append('s');
        }
        return sb.toString();
    }

    public static String formatRemaining(long expiryEpochMillis) {
        long remaining = expiryEpochMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return "expired";
        }
        return format(Duration.ofMillis(remaining));
    }
}
