package me.rexsystems.rexranks.api.node;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * An immutable assignment on a user or group: a permission, inherited group,
 * prefix, suffix, meta entry, weight, or display name.
 */
public final class Node {
    private final NodeType type;
    private final String key;
    private final String value;
    private final boolean negated;
    private final long expiry;
    private final ImmutableContextSet contexts;

    private Node(NodeType type, String key, String value, boolean negated, long expiry, ImmutableContextSet contexts) {
        this.type = Objects.requireNonNull(type, "type");
        this.key = Objects.requireNonNull(key, "key");
        this.value = value == null ? "true" : value;
        this.negated = negated;
        this.expiry = expiry;
        this.contexts = contexts == null ? ImmutableContextSet.empty() : contexts;
    }

    public static Builder builder(NodeType type, String key) {
        return new Builder(type, key);
    }

    public static Node permission(String key) {
        return permission(key, true);
    }

    public static Node permission(String key, boolean value) {
        return builder(NodeType.PERMISSION, normalizePermission(key)).value(Boolean.toString(value)).negated(!value).build();
    }

    public static Node regex(String pattern, boolean value) {
        return builder(NodeType.REGEX_PERMISSION, pattern).value(Boolean.toString(value)).negated(!value).build();
    }

    public static Node inheritance(String group) {
        return builder(NodeType.INHERITANCE, group.toLowerCase(Locale.ROOT)).value("true").build();
    }

    public static Node prefix(int priority, String text) {
        return builder(NodeType.PREFIX, String.valueOf(priority)).value(text).build();
    }

    public static Node suffix(int priority, String text) {
        return builder(NodeType.SUFFIX, String.valueOf(priority)).value(text).build();
    }

    public static Node meta(String metaKey, String metaValue) {
        return builder(NodeType.META, metaKey).value(metaValue).build();
    }

    public static Node weight(int weight) {
        return builder(NodeType.WEIGHT, "weight").value(String.valueOf(weight)).build();
    }

    public static Node displayName(String name) {
        return builder(NodeType.DISPLAY_NAME, "displayname").value(name).build();
    }

    public static String normalizePermission(String key) {
        return key.toLowerCase(Locale.ROOT).trim();
    }

    public NodeType type() {
        return type;
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public boolean negated() {
        return negated;
    }

    public boolean hasExpiry() {
        return expiry > 0;
    }

    public OptionalLong expiry() {
        return hasExpiry() ? OptionalLong.of(expiry) : OptionalLong.empty();
    }

    public long expiryUnix() {
        return expiry;
    }

    public boolean expired() {
        return hasExpiry() && System.currentTimeMillis() >= expiry;
    }

    public boolean expired(long now) {
        return hasExpiry() && now >= expiry;
    }

    public ImmutableContextSet contexts() {
        return contexts;
    }

    public OptionalInt priority() {
        if (type != NodeType.PREFIX && type != NodeType.SUFFIX && type != NodeType.WEIGHT) {
            return OptionalInt.empty();
        }
        try {
            if (type == NodeType.WEIGHT) {
                return OptionalInt.of(Integer.parseInt(value));
            }
            return OptionalInt.of(Integer.parseInt(key));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public String groupName() {
        if (type != NodeType.INHERITANCE) {
            throw new IllegalStateException("Not an inheritance node");
        }
        return key;
    }

    public boolean sameIdentity(Node other) {
        return type == other.type
                && key.equalsIgnoreCase(other.key)
                && contexts.equals(other.contexts);
    }

    public Builder toBuilder() {
        return new Builder(type, key).value(value).negated(negated).expiry(expiry).contexts(contexts);
    }

    /**
     * Canonical string used for dumps and diffs: {@code type key=value [negated] [expiry] [contexts]}.
     */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name().toLowerCase(Locale.ROOT)).append(' ').append(key);
        if (!value.equals("true") || type == NodeType.META || type.isChatMeta()) {
            sb.append('=').append(value);
        }
        if (negated && type.isPermission()) {
            sb.append(" negated");
        }
        if (hasExpiry()) {
            sb.append(" expiry=").append(expiry);
        }
        if (!contexts.isEmpty()) {
            sb.append(' ').append(contexts);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Node other)) {
            return false;
        }
        return type == other.type
                && key.equals(other.key)
                && value.equals(other.value)
                && negated == other.negated
                && expiry == other.expiry
                && contexts.equals(other.contexts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, value, negated, expiry, contexts);
    }

    @Override
    public String toString() {
        return toCompactString();
    }

    public static final class Builder {
        private NodeType type;
        private String key;
        private String value = "true";
        private boolean negated;
        private long expiry;
        private ImmutableContextSet contexts = ImmutableContextSet.empty();

        public Builder(NodeType type, String key) {
            this.type = type;
            this.key = key;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder negated(boolean negated) {
            this.negated = negated;
            return this;
        }

        public Builder expiry(long expiryEpochMillis) {
            this.expiry = expiryEpochMillis;
            return this;
        }

        public Builder expiryDuration(long durationMillis) {
            if (durationMillis <= 0) {
                this.expiry = 0;
            } else {
                this.expiry = System.currentTimeMillis() + durationMillis;
            }
            return this;
        }

        public Builder contexts(ImmutableContextSet contexts) {
            this.contexts = contexts == null ? ImmutableContextSet.empty() : contexts;
            return this;
        }

        public Node build() {
            if (type == NodeType.PERMISSION) {
                this.key = normalizePermission(key);
            }
            if (type == NodeType.INHERITANCE) {
                this.key = key.toLowerCase(Locale.ROOT);
            }
            return new Node(type, key, value, negated, expiry, contexts);
        }
    }
}
