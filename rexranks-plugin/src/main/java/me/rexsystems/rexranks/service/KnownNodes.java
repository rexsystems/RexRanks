package me.rexsystems.rexranks.service;

import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class KnownNodes {
    private final Storage storage;
    private final List<String> configured;

    public KnownNodes(Storage storage, List<String> configured) {
        this.storage = storage;
        this.configured = configured;
    }

    public Set<String> all() {
        Set<String> nodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        nodes.addAll(configured);
        if (Bukkit.getServer() != null) {
            try {
                for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
                    nodes.add(permission.getName());
                }
            } catch (Throwable ignored) {
            }
        }
        for (User user : storage.getLoadedUsers()) {
            collect(user.getNodes(), nodes);
        }
        for (Group group : storage.getLoadedGroups()) {
            collect(group.getNodes(), nodes);
        }
        return nodes;
    }

    public List<String> complete(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return all().stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                .limit(80)
                .collect(Collectors.toList());
    }

    private static void collect(Set<Node> nodes, Set<String> out) {
        for (Node node : nodes) {
            if (node.type() == NodeType.PERMISSION || node.type() == NodeType.REGEX_PERMISSION) {
                out.add(node.key());
            }
        }
    }
}
