package me.rexsystems.rexranks.api.context;

import java.util.Locale;
import java.util.Objects;

/**
 * A single context key-value pair (for example {@code world=nether}).
 */
public final class Context {
    public static final String WORLD = "world";
    public static final String SERVER = "server";
    public static final String DIMENSION = "dimension";
    public static final String GAMEMODE = "gamemode";

    private final String key;
    private final String value;

    public Context(String key, String value) {
        this.key = Objects.requireNonNull(key, "key").toLowerCase(Locale.ROOT);
        this.value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (this.key.isEmpty() || this.value.isEmpty()) {
            throw new IllegalArgumentException("Context key and value must not be empty");
        }
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Context other)) {
            return false;
        }
        return key.equals(other.key) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
