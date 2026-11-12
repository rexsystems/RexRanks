package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LuckPerms-style shorthand nodes ({@code group.admin},
 * {@code prefix.100.&c[Admin]}, {@code weight.50}, {@code meta.key.value},
 * {@code r=^foo\\..*}) into typed {@link Node}s, and the reverse.
 */
public final class NodeShorthand {
    private static final Pattern PREFIX_SUFFIX = Pattern.compile("^(prefix|suffix)\\.(-?\\d+)\\.(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT = Pattern.compile("^weight\\.(-?\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP = Pattern.compile("^group\\.([a-z0-9_\\-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLAY = Pattern.compile("^displayname\\.(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("^meta\\.([^.]+)\\.(.*)$", Pattern.CASE_INSENSITIVE);

    private NodeShorthand() {
    }

    public static Node parse(String raw) {
        return parse(raw, true, 0, ImmutableContextSet.empty());
    }

    public static Node parse(String raw, boolean value, long expiry, ImmutableContextSet contexts) {
        String node = raw;
        boolean negated = !value;
        if (node.startsWith("-")) {
            negated = true;
            node = node.substring(1);
        }
        Matcher ps = PREFIX_SUFFIX.matcher(node);
        if (ps.matches()) {
            NodeType type = ps.group(1).equalsIgnoreCase("prefix") ? NodeType.PREFIX : NodeType.SUFFIX;
            return Node.builder(type, ps.group(2)).value(ps.group(3)).expiry(expiry).contexts(contexts).build();
        }
        Matcher w = WEIGHT.matcher(node);
        if (w.matches()) {
            return Node.builder(NodeType.WEIGHT, "weight").value(w.group(1)).expiry(expiry).contexts(contexts).build();
        }
        Matcher g = GROUP.matcher(node);
        if (g.matches()) {
            return Node.builder(NodeType.INHERITANCE, g.group(1).toLowerCase(Locale.ROOT))
                    .expiry(expiry).contexts(contexts).build();
        }
        Matcher d = DISPLAY.matcher(node);
        if (d.matches()) {
            return Node.builder(NodeType.DISPLAY_NAME, "displayname").value(d.group(1))
                    .expiry(expiry).contexts(contexts).build();
        }
        Matcher m = META.matcher(node);
        if (m.matches()) {
            return Node.builder(NodeType.META, m.group(1)).value(m.group(2))
                    .expiry(expiry).contexts(contexts).build();
        }
        if (node.startsWith("r=") || node.startsWith("R=")) {
            return Node.builder(NodeType.REGEX_PERMISSION, node.substring(2))
                    .value(Boolean.toString(!negated))
                    .negated(negated)
                    .expiry(expiry)
                    .contexts(contexts)
                    .build();
        }
        return Node.builder(NodeType.PERMISSION, Node.normalizePermission(node))
                .value(Boolean.toString(!negated))
                .negated(negated)
                .expiry(expiry)
                .contexts(contexts)
                .build();
    }

    public static String toShorthand(Node node) {
        return switch (node.type()) {
            case PERMISSION -> (node.negated() ? "-" : "") + node.key();
            case REGEX_PERMISSION -> (node.negated() ? "-" : "") + "r=" + node.key();
            case INHERITANCE -> "group." + node.key();
            case PREFIX -> "prefix." + node.key() + "." + node.value();
            case SUFFIX -> "suffix." + node.key() + "." + node.value();
            case META -> "meta." + node.key() + "." + node.value();
            case WEIGHT -> "weight." + node.value();
            case DISPLAY_NAME -> "displayname." + node.value();
        };
    }

    public static Optional<Node> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
