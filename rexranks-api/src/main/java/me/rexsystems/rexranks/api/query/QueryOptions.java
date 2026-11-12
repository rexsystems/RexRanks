package me.rexsystems.rexranks.api.query;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;

import java.util.Objects;

/**
 * Options used when resolving a subject's permissions or meta.
 */
public final class QueryOptions {
    public static final QueryOptions DEFAULT = new QueryOptions(ImmutableContextSet.empty(), true, true);

    private final ImmutableContextSet contexts;
    private final boolean includeInherited;
    private final boolean applyExpiredFilter;

    private QueryOptions(ImmutableContextSet contexts, boolean includeInherited, boolean applyExpiredFilter) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.includeInherited = includeInherited;
        this.applyExpiredFilter = applyExpiredFilter;
    }

    public static QueryOptions defaultContextual() {
        return DEFAULT;
    }

    public static QueryOptions contextual(ImmutableContextSet contexts) {
        return new QueryOptions(contexts, true, true);
    }

    public static QueryOptions of(ImmutableContextSet contexts, boolean includeInherited) {
        return new QueryOptions(contexts, includeInherited, true);
    }

    public ImmutableContextSet contexts() {
        return contexts;
    }

    public boolean includeInherited() {
        return includeInherited;
    }

    public boolean applyExpiredFilter() {
        return applyExpiredFilter;
    }

    public QueryOptions withContexts(ImmutableContextSet contexts) {
        return new QueryOptions(contexts, includeInherited, applyExpiredFilter);
    }

    public QueryOptions withInherited(boolean includeInherited) {
        return new QueryOptions(contexts, includeInherited, applyExpiredFilter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryOptions other)) {
            return false;
        }
        return includeInherited == other.includeInherited
                && applyExpiredFilter == other.applyExpiredFilter
                && contexts.equals(other.contexts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contexts, includeInherited, applyExpiredFilter);
    }
}
