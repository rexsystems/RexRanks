package me.rexsystems.rexranks.api.event;

/**
 * A handle that can be used to unsubscribe from an event.
 */
public interface EventSubscription extends AutoCloseable {
    @Override
    void close();

    boolean isActive();
}
