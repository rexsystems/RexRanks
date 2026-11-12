package me.rexsystems.rexranks.storage;

import me.rexsystems.rexranks.api.context.Context;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts nodes to and from plain maps, used by both YAML and SQL storage.
 */
public final class NodeCodec {
    private NodeCodec() {
    }

    public static Map<String, Object> toMap(Node node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", node.type().name().toLowerCase(Locale.ROOT));
        map.put("key", node.key());
        map.put("value", node.value());
        map.put("negated", node.negated());
        map.put("expiry", node.expiryUnix());
        if (!node.contexts().isEmpty()) {
            Map<String, List<String>> ctx = new LinkedHashMap<>();
            for (Context context : node.contexts().toSet()) {
                ctx.computeIfAbsent(context.key(), k -> new ArrayList<>()).add(context.value());
            }
            map.put("contexts", ctx);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Node fromMap(Map<String, Object> map) {
        NodeType type = NodeType.valueOf(String.valueOf(map.get("type")).toUpperCase(Locale.ROOT));
        String key = String.valueOf(map.get("key"));
        String value = map.get("value") == null ? "true" : String.valueOf(map.get("value"));
        boolean negated = asBoolean(map.get("negated"));
        long expiry = asLong(map.get("expiry"));
        ImmutableContextSet contexts = ImmutableContextSet.empty();
        Object raw = map.get("contexts");
        if (raw instanceof Map<?, ?> ctxMap) {
            ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
            ctxMap.forEach((k, v) -> {
                if (v instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        builder.add(String.valueOf(k), String.valueOf(item));
                    }
                } else if (v != null) {
                    builder.add(String.valueOf(k), String.valueOf(v));
                }
            });
            contexts = builder.build();
        }
        return Node.builder(type, key).value(value).negated(negated).expiry(expiry).contexts(contexts).build();
    }

    public static List<Map<String, Object>> toMaps(Collection<Node> nodes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Node node : nodes) {
            list.add(toMap(node));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static List<Node> fromMaps(Object raw) {
        List<Node> nodes = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    nodes.add(fromMap((Map<String, Object>) map));
                } else if (item instanceof String shorthand) {
                    nodes.add(me.rexsystems.rexranks.engine.NodeShorthand.parse(shorthand));
                }
            }
        }
        return nodes;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
