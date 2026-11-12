package me.rexsystems.rexranks.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses MiniMessage, legacy {@code &} codes, and {@code &#RRGGBB} / {@code #RRGGBB} hex
 * into Adventure components.
 */
public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HASH_HEX = Pattern.compile("(?<![&<])#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private Text() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(toMiniMessage(input));
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return parse(input);
        }
        String text = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            text = text.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return parse(text);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        return MINI.deserialize(toMiniMessage(input), TagResolver.resolver(resolvers));
    }

    public static Component parse(String input, String key, String value) {
        return parse(input, TagResolver.resolver(Placeholder.parsed(key, toMiniMessage(value))));
    }

    /**
     * Converts legacy {@code &}/{@code &#} and loose {@code #RRGGBB} into MiniMessage,
     * leaving existing MiniMessage tags untouched.
     */
    public static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = AMP_HEX.matcher(input).replaceAll("<#$1>");
        text = HASH_HEX.matcher(text).replaceAll("<#$1>");
        Matcher matcher = LEGACY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(out, tag == null ? matcher.group() : "<" + tag + ">");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String legacy(Component component) {
        return LEGACY_AMP.serialize(component);
    }

    public static Component clickable(String label, String command, String hover) {
        return parse(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(parse(hover)));
    }

    public static Component suggestable(String label, String suggestion, String hover) {
        return parse(label)
                .clickEvent(ClickEvent.suggestCommand(suggestion))
                .hoverEvent(HoverEvent.showText(parse(hover)));
    }

    public static Component url(String label, String url, String hover) {
        return parse(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(parse(hover)));
    }

    public static Component copy(String label, String value, String hover) {
        return parse(label)
                .clickEvent(ClickEvent.copyToClipboard(value))
                .hoverEvent(HoverEvent.showText(parse(hover)));
    }

    public static String escape(String text) {
        return MiniMessage.miniMessage().escapeTags(text == null ? "" : text);
    }
}
