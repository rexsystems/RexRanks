package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.data.CachedData;
import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.engine.PermissionCalculator;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public final class UserImpl implements User {
    private final UUID uniqueId;
    private volatile String username;
    private volatile String primaryGroup;
    private final NodeMapImpl nodes;
    private final CachedDataImpl cached;
    private volatile long updatedAt;

    public UserImpl(UUID uniqueId, String username, String primaryGroup,
                    PermissionCalculator calculator, Supplier<QueryOptions> defaultOptions) {
        this.uniqueId = uniqueId;
        this.username = username == null ? uniqueId.toString() : username;
        this.primaryGroup = primaryGroup == null ? "default" : primaryGroup.toLowerCase(Locale.ROOT);
        this.nodes = new NodeMapImpl(this::touch);
        this.cached = new CachedDataImpl(nodes::toSet, this::getPrimaryGroup, calculator, defaultOptions);
        this.updatedAt = System.currentTimeMillis();
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        if (username != null && !username.isBlank()) {
            this.username = username;
            touch();
        }
    }

    @Override
    public String getPrimaryGroup() {
        return primaryGroup;
    }

    @Override
    public void setPrimaryGroup(String group) {
        this.primaryGroup = group.toLowerCase(Locale.ROOT);
        cached.invalidate();
        touch();
    }

    @Override
    public NodeMap data() {
        return nodes;
    }

    public NodeMapImpl dataImpl() {
        return nodes;
    }

    @Override
    public CachedData getCachedData() {
        return cached;
    }

    @Override
    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public void invalidateCache() {
        cached.invalidate();
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
        cached.invalidate();
    }

    public UserImpl copy(PermissionCalculator calculator, Supplier<QueryOptions> defaultOptions) {
        UserImpl copy = new UserImpl(uniqueId, username, primaryGroup, calculator, defaultOptions);
        copy.nodes.replaceAll(this.nodes.toList());
        copy.updatedAt = this.updatedAt;
        return copy;
    }
}
