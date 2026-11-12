package me.rexsystems.rexranks.api.event;

import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;

/**
 * Fired after a node is added to or removed from a group.
 */
public class GroupPermissionChangeEvent {
    public enum Action { ADD, REMOVE, REPLACE }

    private final Group group;
    private final Node node;
    private final Node previous;
    private final Action action;

    public GroupPermissionChangeEvent(Group group, Node node, Node previous, Action action) {
        this.group = group;
        this.node = node;
        this.previous = previous;
        this.action = action;
    }

    public Group getGroup() {
        return group;
    }

    public Node getNode() {
        return node;
    }

    public Node getPrevious() {
        return previous;
    }

    public Action getAction() {
        return action;
    }
}
