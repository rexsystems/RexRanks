package me.rexsystems.rexranks.api.event;

import me.rexsystems.rexranks.api.user.User;

/**
 * Fired when a user's inherited groups or primary group change.
 */
public class UserGroupChangeEvent {
    public enum Action { PARENT_ADD, PARENT_REMOVE, PRIMARY_CHANGE }

    private final User user;
    private final Action action;
    private final String group;
    private final String previousPrimary;

    public UserGroupChangeEvent(User user, Action action, String group, String previousPrimary) {
        this.user = user;
        this.action = action;
        this.group = group;
        this.previousPrimary = previousPrimary;
    }

    public User getUser() {
        return user;
    }

    public Action getAction() {
        return action;
    }

    public String getGroup() {
        return group;
    }

    public String getPreviousPrimary() {
        return previousPrimary;
    }
}
