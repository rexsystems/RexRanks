package me.rexsystems.rexranks.api.event;

import java.util.function.Consumer;

/**
 * Platform-neutral event bus. Bukkit plugins can also listen to the
 * corresponding {@code org.bukkit.event} wrappers fired by the plugin.
 */
public interface EventBus {
    /**
     * Subscribe to an event type. {@code owner} is typically the calling plugin
     * and is used to clean up subscriptions when that plugin disables.
     */
    <T> EventSubscription subscribe(Object owner, Class<T> type, Consumer<T> handler);

    <T> void post(T event);

    void unregisterAll(Object owner);
}
