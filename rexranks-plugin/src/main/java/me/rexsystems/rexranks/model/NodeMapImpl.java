package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class NodeMapImpl implements NodeMap {
    private final CopyOnWriteArrayList<Node> nodes = new CopyOnWriteArrayList<>();
    private final Runnable onChange;

    public NodeMapImpl() {
        this(() -> {});
    }

    public NodeMapImpl(Runnable onChange) {
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    @Override
    public Set<Node> toSet() {
        return Set.copyOf(nodes);
    }

    public List<Node> toList() {
        return List.copyOf(nodes);
    }

    @Override
    public boolean add(Node node) {
        if (nodes.contains(node)) {
            return false;
        }
        nodes.add(node);
        onChange.run();
        return true;
    }

    @Override
    public boolean addOrReplace(Node node) {
        boolean replaced = false;
        for (int i = 0; i < nodes.size(); i++) {
            Node existing = nodes.get(i);
            if (existing.sameIdentity(node)) {
                if (existing.equals(node)) {
                    return false;
                }
                nodes.set(i, node);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            nodes.add(node);
        }
        onChange.run();
        return true;
    }

    @Override
    public boolean remove(Node node) {
        boolean removed = nodes.remove(node);
        if (!removed) {
            Iterator<Node> it = nodes.iterator();
            List<Node> drop = new ArrayList<>();
            for (Node existing : nodes) {
                if (existing.sameIdentity(node)) {
                    drop.add(existing);
                }
            }
            removed = nodes.removeAll(drop);
        }
        if (removed) {
            onChange.run();
        }
        return removed;
    }

    @Override
    public int removeIf(Predicate<Node> predicate) {
        List<Node> drop = nodes.stream().filter(predicate).toList();
        if (drop.isEmpty()) {
            return 0;
        }
        nodes.removeAll(drop);
        onChange.run();
        return drop.size();
    }

    @Override
    public int removeMatching(NodeType type, String key) {
        return removeIf(n -> n.type() == type && n.key().equalsIgnoreCase(key));
    }

    @Override
    public void clear() {
        if (nodes.isEmpty()) {
            return;
        }
        nodes.clear();
        onChange.run();
    }

    @Override
    public void clear(NodeType type) {
        removeIf(n -> n.type() == type);
    }

    @Override
    public boolean contains(Node node) {
        return nodes.contains(node) || nodes.stream().anyMatch(n -> n.sameIdentity(node));
    }

    @Override
    public int size() {
        return nodes.size();
    }

    public void replaceAll(Iterable<Node> incoming) {
        nodes.clear();
        for (Node node : incoming) {
            nodes.add(node);
        }
        onChange.run();
    }

    @Override
    public String toString() {
        return nodes.stream().map(Node::toCompactString).collect(Collectors.joining(", ", "[", "]"));
    }
}
