package me.rexsystems.rexranks.api.node;

/**
 * Kinds of nodes that can be assigned to a user or group.
 */
public enum NodeType {
    PERMISSION,
    REGEX_PERMISSION,
    INHERITANCE,
    PREFIX,
    SUFFIX,
    META,
    WEIGHT,
    DISPLAY_NAME;

    public boolean isPermission() {
        return this == PERMISSION || this == REGEX_PERMISSION;
    }

    public boolean isChatMeta() {
        return this == PREFIX || this == SUFFIX;
    }
}
