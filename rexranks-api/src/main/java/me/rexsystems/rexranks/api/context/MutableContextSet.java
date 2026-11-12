package me.rexsystems.rexranks.api.context;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A mutable context set used when assembling a query or a node.
 */
public final class MutableContextSet {
    private final Set<Context> contexts = new LinkedHashSet<>();

    public static MutableContextSet create() {
        return new MutableContextSet();
    }

    public static MutableContextSet of(Set<Context> contexts) {
        MutableContextSet set = new MutableContextSet();
        set.contexts.addAll(contexts);
        return set;
    }

    public MutableContextSet add(String key, String value) {
        contexts.add(new Context(key, value));
        return this;
    }

    public MutableContextSet add(Context context) {
        contexts.add(context);
        return this;
    }

    public MutableContextSet remove(String key, String value) {
        contexts.remove(new Context(key, value));
        return this;
    }

    public MutableContextSet clear() {
        contexts.clear();
        return this;
    }

    public boolean isEmpty() {
        return contexts.isEmpty();
    }

    public ImmutableContextSet immutable() {
        return ImmutableContextSet.of(contexts);
    }
}
