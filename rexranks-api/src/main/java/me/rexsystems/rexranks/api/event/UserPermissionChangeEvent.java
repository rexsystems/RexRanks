package me.rexsystems.rexranks.api.event;

import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.user.User;

/**
 * Fired after a node is added to or removed from a user.
 */
public class UserPermissionChangeEvent {
    public enum Action { ADD, REMOVE, REPLACE }

    private final User user;
    private final Node node;
    private final Node previous;
    private final Action action;

    public UserPermissionChangeEvent(User user, Node node, Node previous, Action action) {
        this.user = user;
        this.node = node;
        this.previous = previous;
        this.action = action;
    }

    public User getUser() {
        return user;
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
