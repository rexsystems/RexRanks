package me.rexsystems.rexranks.api.query;

/**
 * A three-state boolean used for permission results.
 */
public enum Tristate {
    TRUE,
    FALSE,
    UNDEFINED;

    public boolean asBoolean() {
        return this == TRUE;
    }

    public boolean isDefined() {
        return this != UNDEFINED;
    }

    public Tristate negate() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNDEFINED -> UNDEFINED;
        };
    }

    public static Tristate of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static Tristate ofNullable(Boolean value) {
        if (value == null) {
            return UNDEFINED;
        }
        return of(value);
    }
}
