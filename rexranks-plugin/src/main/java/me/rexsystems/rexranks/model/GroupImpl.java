package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.data.CachedData;
import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.engine.PermissionCalculator;

import java.util.Locale;
import java.util.function.Supplier;

public final class GroupImpl implements Group {
    private final String name;
    private final NodeMapImpl nodes;
    private final CachedDataImpl cached;
    private volatile long updatedAt;

    public GroupImpl(String name, PermissionCalculator calculator, Supplier<QueryOptions> defaultOptions) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.nodes = new NodeMapImpl(this::touch);
        this.cached = new CachedDataImpl(nodes::toSet, () -> null, calculator, defaultOptions);
        this.updatedAt = System.currentTimeMillis();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return nodes.ofType(NodeType.DISPLAY_NAME).stream()
                .map(Node::value)
                .findFirst()
                .orElse(name);
    }

    @Override
    public int getWeight() {
        return nodes.ofType(NodeType.WEIGHT).stream()
                .mapToInt(n -> n.priority().orElse(0))
                .max()
                .orElse(0);
    }

    public void setWeight(int weight) {
        nodes.removeMatching(NodeType.WEIGHT, "weight");
        nodes.add(Node.weight(weight));
    }

    public void setDisplayName(String displayName) {
        nodes.removeMatching(NodeType.DISPLAY_NAME, "displayname");
        if (displayName != null && !displayName.isBlank()) {
            nodes.add(Node.displayName(displayName));
        }
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
}
