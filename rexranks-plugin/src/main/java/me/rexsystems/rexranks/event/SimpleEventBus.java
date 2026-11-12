package me.rexsystems.rexranks.event;

import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.api.event.EventSubscription;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleEventBus implements EventBus {
    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();
    private final Logger logger;

    public SimpleEventBus(Logger logger) {
        this.logger = logger;
    }

    @Override
    public <T> EventSubscription subscribe(Object owner, Class<T> type, Consumer<T> handler) {
        Subscription<T> subscription = new Subscription<>(owner, type, handler);
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public <T> void post(T event) {
        for (Subscription<?> subscription : subscriptions) {
            subscription.tryDispatch(event);
        }
    }

    @Override
    public void unregisterAll(Object owner) {
        subscriptions.removeIf(s -> Objects.equals(s.owner, owner));
    }

    private final class Subscription<T> implements EventSubscription {
        private final Object owner;
        private final Class<T> type;
        private final Consumer<T> handler;
        private volatile boolean active = true;

        private Subscription(Object owner, Class<T> type, Consumer<T> handler) {
            this.owner = owner;
            this.type = type;
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        private void tryDispatch(Object event) {
            if (!active || !type.isInstance(event)) {
                return;
            }
            try {
                handler.accept((T) event);
            } catch (Exception e) {
                if (logger != null) {
                    logger.log(Level.WARNING, "RexRanks event handler for " + type.getSimpleName() + " threw", e);
                }
            }
        }

        @Override
        public void close() {
            active = false;
            subscriptions.remove(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
