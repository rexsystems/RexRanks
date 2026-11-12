package me.rexsystems.rexranks.api.event;

/**
 * Fired after a full data reload or a sync pull from another server.
 */
public class DataReloadEvent {
    private final String reason;

    public DataReloadEvent(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
