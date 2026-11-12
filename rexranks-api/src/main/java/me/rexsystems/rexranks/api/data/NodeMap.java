package me.rexsystems.rexranks.api.data;

import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;

import java.util.Collection;
import java.util.Set;

/**
 * Mutable node collection on a user or group. Changes are in-memory until the
 * subject is saved.
 */
public interface NodeMap {
    Set<Node> toSet();

    default Set<Node> ofType(NodeType type) {
        return toSet().stream()
                .filter(n -> n.type() == type)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    boolean add(Node node);

    /**
     * Adds {@code node}, replacing any existing node with the same identity
     * (type, key, contexts).
     */
    boolean addOrReplace(Node node);

    boolean remove(Node node);

    int removeIf(java.util.function.Predicate<Node> predicate);

    int removeMatching(NodeType type, String key);

    void clear();

    void clear(NodeType type);

    boolean contains(Node node);

    int size();

    default void addAll(Collection<Node> nodes) {
        for (Node node : nodes) {
            addOrReplace(node);
        }
    }
}
