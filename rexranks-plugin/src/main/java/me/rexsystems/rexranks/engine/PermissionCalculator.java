package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.data.CachedPermissionData;
import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.user.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves inherited, contextual, temporary nodes into a permission check and
 * a meta snapshot. Pure Java; no Bukkit dependency.
 *
 * <p>Specificity wins, then direct (user) nodes beat inherited, then higher
 * group weight, then more recent insertion. Equal remaining ties prefer a
 * denial so negated nodes are safe defaults.
 */
public final class PermissionCalculator {
    private final GroupLookup groups;
    private final String defaultGroup;
    private final boolean autoInheritDefault;

    public PermissionCalculator(GroupLookup groups, String defaultGroup, boolean autoInheritDefault) {
        this.groups = groups;
        this.defaultGroup = defaultGroup == null ? "default" : defaultGroup.toLowerCase(Locale.ROOT);
        this.autoInheritDefault = autoInheritDefault;
    }

    public List<ResolvedNode> resolve(Iterable<Node> ownNodes, String primaryGroup, QueryOptions options) {
        return resolve(ownNodes, primaryGroup, options, autoInheritDefault);
    }

    public List<ResolvedNode> resolve(Iterable<Node> ownNodes, String primaryGroup, QueryOptions options, boolean inheritDefault) {
        List<ResolvedNode> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        collect(ownNodes, "self", 0, 0, options, now, out);

        Set<String> inherited = new LinkedHashSet<>();
        if (primaryGroup != null && !primaryGroup.isBlank()) {
            inherited.add(primaryGroup.toLowerCase(Locale.ROOT));
        }
        for (Node node : ownNodes) {
            if (node.type() == NodeType.INHERITANCE && applies(node, options, now)) {
                inherited.add(node.groupName());
            }
        }
        if (inheritDefault) {
            inherited.add(defaultGroup);
        }
        Set<String> visited = new HashSet<>();
        for (String name : inherited) {
            walkGroup(name, 1, options, now, out, visited);
        }
        return out;
    }

    public List<ResolvedNode> resolve(User user, QueryOptions options) {
        return resolve(user.data().toSet(), user.getPrimaryGroup(), options);
    }

    public List<ResolvedNode> resolve(Group group, QueryOptions options) {
        return resolve(group.data().toSet(), null, options, false);
    }

    public Tristate check(Iterable<Node> ownNodes, String primaryGroup, String permission, QueryOptions options) {
        List<ResolvedNode> resolved = resolve(ownNodes, primaryGroup, options);
        return check(resolved, permission);
    }

    public Tristate check(List<ResolvedNode> resolved, String permission) {
        ResolvedNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ResolvedNode candidate : resolved) {
            Node node = candidate.node();
            if (!node.type().isPermission()) {
                continue;
            }
            int score = WildcardMatcher.score(node.key(), permission, node.type() == NodeType.REGEX_PERMISSION);
            if (score < 0) {
                continue;
            }
            if (best == null || better(candidate, score, best, bestScore)) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) {
            return Tristate.UNDEFINED;
        }
        return best.node().negated() ? Tristate.FALSE : Tristate.TRUE;
    }

    public CachedPermissionData permissionData(Iterable<Node> ownNodes, String primaryGroup, QueryOptions options) {
        List<ResolvedNode> resolved = resolve(ownNodes, primaryGroup, options);
        Map<String, Tristate> map = new LinkedHashMap<>();
        Set<String> groupNames = new LinkedHashSet<>();
        for (ResolvedNode rn : resolved) {
            Node node = rn.node();
            if (node.type() == NodeType.INHERITANCE) {
                groupNames.add(node.groupName());
            }
            if (node.type() == NodeType.PERMISSION && !node.key().contains("*")) {
                Tristate existing = map.get(node.key());
                Tristate next = node.negated() ? Tristate.FALSE : Tristate.TRUE;
                if (existing == null) {
                    map.put(node.key(), next);
                }
            }
        }
        // Recompute exact entries through the full matcher so wildcards apply.
        Map<String, Tristate> exact = new LinkedHashMap<>();
        for (String key : map.keySet()) {
            exact.put(key, check(resolved, key));
        }
        return new CalculatedPermissionData(resolved, exact, Set.copyOf(groupNames), this);
    }

    public CachedMetaData metaData(Iterable<Node> ownNodes, String primaryGroup, QueryOptions options) {
        List<ResolvedNode> resolved = resolve(ownNodes, primaryGroup, options);
        List<Node> prefixes = new ArrayList<>();
        List<Node> suffixes = new ArrayList<>();
        Map<String, List<String>> meta = new LinkedHashMap<>();
        int weight = 0;
        String displayName = "";
        Set<String> groups = new LinkedHashSet<>();
        for (ResolvedNode rn : resolved) {
            Node node = rn.node();
            switch (node.type()) {
                case PREFIX -> prefixes.add(node);
                case SUFFIX -> suffixes.add(node);
                case META -> meta.computeIfAbsent(node.key(), k -> new ArrayList<>()).add(node.value());
                case WEIGHT -> {
                    if (rn.isDirect()) {
                        weight = Math.max(weight, node.priority().orElse(0));
                    } else {
                        weight = Math.max(weight, rn.groupWeight());
                    }
                }
                case DISPLAY_NAME -> {
                    if (displayName.isEmpty()) {
                        displayName = node.value();
                    }
                }
                case INHERITANCE -> groups.add(node.groupName());
                default -> {
                }
            }
        }
        Comparator<Node> byPriority = Comparator.<Node>comparingInt(n -> n.priority().orElse(0)).reversed();
        prefixes.sort(byPriority);
        suffixes.sort(byPriority);
        String prefix = prefixes.isEmpty() ? "" : prefixes.getFirst().value();
        String suffix = suffixes.isEmpty() ? "" : suffixes.getFirst().value();
        if (weight == 0) {
            for (ResolvedNode rn : resolved) {
                if (!rn.isDirect()) {
                    weight = Math.max(weight, rn.groupWeight());
                }
            }
        }
        String primary = primaryGroup == null ? defaultGroup : primaryGroup;
        return new CalculatedMetaData(
                prefix,
                suffix,
                prefixes.stream().map(Node::value).toList(),
                suffixes.stream().map(Node::value).toList(),
                meta,
                weight,
                primary,
                displayName
        );
    }

    private void walkGroup(String name, int depth, QueryOptions options, long now,
                           List<ResolvedNode> out, Set<String> visited) {
        String id = name.toLowerCase(Locale.ROOT);
        if (!visited.add(id)) {
            return;
        }
        Group group = groups.get(id).orElse(null);
        if (group == null) {
            return;
        }
        int weight = group.getWeight();
        collect(group.data().toSet(), group.getName(), depth, weight, options, now, out);
        for (Node node : group.data().toSet()) {
            if (node.type() == NodeType.INHERITANCE && applies(node, options, now)) {
                walkGroup(node.groupName(), depth + 1, options, now, out, visited);
            }
        }
    }

    private void collect(Iterable<Node> nodes, String origin, int depth, int weight,
                         QueryOptions options, long now, List<ResolvedNode> out) {
        int order = 0;
        for (Node node : nodes) {
            order++;
            if (!applies(node, options, now)) {
                continue;
            }
            out.add(new ResolvedNode(node, origin, depth, weight, order));
        }
    }

    private boolean applies(Node node, QueryOptions options, long now) {
        if (options.applyExpiredFilter() && node.expired(now)) {
            return false;
        }
        return node.contexts().isSatisfiedBy(options.contexts());
    }

    private static boolean better(ResolvedNode candidate, int score, ResolvedNode best, int bestScore) {
        if (score != bestScore) {
            return score > bestScore;
        }
        if (candidate.depth() != best.depth()) {
            return candidate.depth() < best.depth();
        }
        if (candidate.groupWeight() != best.groupWeight()) {
            return candidate.groupWeight() > best.groupWeight();
        }
        if (candidate.order() != best.order()) {
            return candidate.order() > best.order();
        }
        // Prefer a denial on a complete tie.
        return candidate.node().negated() && !best.node().negated();
    }

    public static final class CalculatedPermissionData implements CachedPermissionData {
        private final List<ResolvedNode> resolved;
        private final Map<String, Tristate> map;
        private final Set<String> groups;
        private final PermissionCalculator calculator;

        CalculatedPermissionData(List<ResolvedNode> resolved, Map<String, Tristate> map,
                                 Set<String> groups, PermissionCalculator calculator) {
            this.resolved = List.copyOf(resolved);
            this.map = Map.copyOf(map);
            this.groups = groups;
            this.calculator = calculator;
        }

        @Override
        public Tristate check(String permission) {
            return calculator.check(resolved, permission);
        }

        @Override
        public Map<String, Tristate> getPermissionMap() {
            return map;
        }

        @Override
        public Set<String> getGroups() {
            return groups;
        }

        public List<ResolvedNode> resolved() {
            return resolved;
        }
    }

    public static final class CalculatedMetaData implements CachedMetaData {
        private final String prefix;
        private final String suffix;
        private final List<String> prefixes;
        private final List<String> suffixes;
        private final Map<String, List<String>> meta;
        private final int weight;
        private final String primaryGroup;
        private final String displayName;

        CalculatedMetaData(String prefix, String suffix, List<String> prefixes, List<String> suffixes,
                           Map<String, List<String>> meta, int weight, String primaryGroup, String displayName) {
            this.prefix = prefix == null ? "" : prefix;
            this.suffix = suffix == null ? "" : suffix;
            this.prefixes = List.copyOf(prefixes);
            this.suffixes = List.copyOf(suffixes);
            Map<String, List<String>> copy = new HashMap<>();
            meta.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            this.meta = Map.copyOf(copy);
            this.weight = weight;
            this.primaryGroup = primaryGroup;
            this.displayName = displayName == null ? "" : displayName;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public String getSuffix() {
            return suffix;
        }

        @Override
        public List<String> getPrefixes() {
            return prefixes;
        }

        @Override
        public List<String> getSuffixes() {
            return suffixes;
        }

        @Override
        public java.util.Optional<String> getMeta(String key) {
            List<String> values = meta.get(key);
            if (values == null || values.isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(values.getFirst());
        }

        @Override
        public Map<String, List<String>> getMetaMap() {
            return meta;
        }

        @Override
        public int getWeight() {
            return weight;
        }

        @Override
        public String getPrimaryGroup() {
            return primaryGroup;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }
}
