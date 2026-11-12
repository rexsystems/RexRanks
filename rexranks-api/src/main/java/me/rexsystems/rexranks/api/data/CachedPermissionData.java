package me.rexsystems.rexranks.api.data;

import me.rexsystems.rexranks.api.query.Tristate;

import java.util.Map;
import java.util.Set;

/**
 * Resolved permission data for a subject under a specific query.
 */
public interface CachedPermissionData {
    Tristate check(String permission);

    default boolean checkBoolean(String permission) {
        return check(permission).asBoolean();
    }

    /**
     * Explicit (non-wildcard) permission nodes contributing to this cache,
     * mapped to their resulting tristate.
     */
    Map<String, Tristate> getPermissionMap();

    Set<String> getGroups();
}
