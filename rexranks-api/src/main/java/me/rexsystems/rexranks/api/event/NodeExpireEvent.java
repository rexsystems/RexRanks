package me.rexsystems.rexranks.api.event;

import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.user.User;

/**
 * Fired when a temporary node expires and is removed.
 */
public class NodeExpireEvent {
    private final User user;
    private final Group group;
    private final Node node;

    public NodeExpireEvent(User user, Group group, Node node) {
        this.user = user;
        this.group = group;
        this.node = node;
    }

    public User getUser() {
        return user;
    }

    public Group getGroup() {
        return group;
    }

    public Node getNode() {
        return node;
    }

    public boolean isUser() {
        return user != null;
    }
}
