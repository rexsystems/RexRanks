package me.rexsystems.rexranks.api.event;

/**
 * An event that listeners can cancel.
 */
public interface Cancellable {
    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
