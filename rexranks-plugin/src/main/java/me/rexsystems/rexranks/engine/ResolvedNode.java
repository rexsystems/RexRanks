package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.node.Node;

/**
 * A node together with where it came from, used while resolving a check.
 *
 * @param depth        0 for a node on the subject itself, 1+ for inherited groups
 * @param groupWeight  weight of the origin group (0 for a direct user node)
 * @param order        insertion order, higher is more recent
 */
public record ResolvedNode(Node node, String origin, int depth, int groupWeight, int order) {
    public boolean isDirect() {
        return depth == 0;
    }
}
