package me.rexsystems.rexranks.api.user;

import me.rexsystems.rexranks.api.data.CachedData;
import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;

import java.util.Set;
import java.util.UUID;

/**
 * A player (online or offline) and their assigned nodes.
 */
public interface User {
    UUID getUniqueId();

    String getUsername();

    void setUsername(String username);

    String getPrimaryGroup();

    void setPrimaryGroup(String group);

    NodeMap data();

    CachedData getCachedData();

    default Tristate checkPermission(String permission) {
        return getCachedData().getPermissionData().check(permission);
    }

    default Tristate checkPermission(String permission, QueryOptions options) {
        return getCachedData().getPermissionData(options).check(permission);
    }

    default Set<Node> getNodes() {
        return data().toSet();
    }

    long getUpdatedAt();

    void invalidateCache();
}
