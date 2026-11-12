package me.rexsystems.rexranks.api.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable set of contexts. A node with this set applies when every pair
 * here is present on the query (the node's contexts are a subset of the query).
 */
public final class ImmutableContextSet {
    public static final ImmutableContextSet EMPTY = new ImmutableContextSet(Set.of());

    private final Set<Context> contexts;

    private ImmutableContextSet(Set<Context> contexts) {
        this.contexts = contexts;
    }

    public static ImmutableContextSet empty() {
        return EMPTY;
    }

    public static ImmutableContextSet of(String key, String value) {
        return new ImmutableContextSet(Set.of(new Context(key, value)));
    }

    public static ImmutableContextSet of(Set<Context> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return EMPTY;
        }
        return new ImmutableContextSet(Set.copyOf(contexts));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return contexts.isEmpty();
    }

    public int size() {
        return contexts.size();
    }

    public Set<Context> toSet() {
        return contexts;
    }

    public boolean contains(Context context) {
        return contexts.contains(context);
    }

    public boolean contains(String key, String value) {
        return contexts.contains(new Context(key, value));
    }

    public Optional<String> getAny(String key) {
        return contexts.stream()
                .filter(c -> c.key().equalsIgnoreCase(key))
                .map(Context::value)
                .findFirst();
    }

    public Set<String> getAll(String key) {
        return contexts.stream()
                .filter(c -> c.key().equalsIgnoreCase(key))
                .map(Context::value)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns true if every context in this set is present in {@code other}.
     * An empty set is satisfied by any query (global nodes always apply).
     */
    public boolean isSatisfiedBy(ImmutableContextSet other) {
        if (isEmpty()) {
            return true;
        }
        return other.contexts.containsAll(this.contexts);
    }

    public MutableContextSet mutableCopy() {
        return MutableContextSet.of(contexts);
    }

    public Map<String, Set<String>> toMap() {
        return contexts.stream().collect(Collectors.groupingBy(
                Context::key,
                Collectors.mapping(Context::value, Collectors.toSet())
        ));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableContextSet other)) {
            return false;
        }
        return contexts.equals(other.contexts);
    }

    @Override
    public int hashCode() {
        return contexts.hashCode();
    }

    @Override
    public String toString() {
        if (contexts.isEmpty()) {
            return "{}";
        }
        return contexts.stream().map(Context::toString).collect(Collectors.joining(", ", "{", "}"));
    }

    public static final class Builder {
        private final Set<Context> contexts = new LinkedHashSet<>();

        public Builder add(String key, String value) {
            contexts.add(new Context(key, value));
            return this;
        }

        public Builder add(Context context) {
            contexts.add(Objects.requireNonNull(context, "context"));
            return this;
        }

        public Builder addAll(Iterable<Context> more) {
            for (Context context : more) {
                contexts.add(context);
            }
            return this;
        }

        public ImmutableContextSet build() {
            if (contexts.isEmpty()) {
                return EMPTY;
            }
            return new ImmutableContextSet(Collections.unmodifiableSet(new LinkedHashSet<>(contexts)));
        }
    }
}
