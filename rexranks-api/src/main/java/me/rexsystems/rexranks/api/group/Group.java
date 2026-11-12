package me.rexsystems.rexranks.api.group;

import me.rexsystems.rexranks.api.data.CachedData;
import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;

import java.util.Set;

/**
 * A permission group.
 */
public interface Group {
    String getName();

    String getDisplayName();

    int getWeight();

    NodeMap data();

    CachedData getCachedData();

    default Set<Node> getNodes() {
        return data().toSet();
    }

    default Set<String> getParents() {
        return data().ofType(NodeType.INHERITANCE).stream()
                .map(Node::groupName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    long getUpdatedAt();

    void invalidateCache();
}
