package me.rexsystems.rexranks.storage;

import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.engine.PermissionCalculator;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared factories used by every storage backend to construct model objects.
 */
public final class StorageContext {
    private final PermissionCalculator calculator;
    private final Supplier<QueryOptions> defaultOptions;
    private final EventBus eventBus;
    private final boolean updatePrimaryOnTrack;
    private final String defaultGroup;

    public StorageContext(PermissionCalculator calculator, Supplier<QueryOptions> defaultOptions,
                          EventBus eventBus, boolean updatePrimaryOnTrack, String defaultGroup) {
        this.calculator = calculator;
        this.defaultOptions = defaultOptions;
        this.eventBus = eventBus;
        this.updatePrimaryOnTrack = updatePrimaryOnTrack;
        this.defaultGroup = defaultGroup;
    }

    public PermissionCalculator calculator() {
        return calculator;
    }

    public Supplier<QueryOptions> defaultOptions() {
        return defaultOptions;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public String defaultGroup() {
        return defaultGroup;
    }

    public UserImpl newUser(UUID uuid, String username, String primaryGroup) {
        String group = primaryGroup == null || primaryGroup.isBlank() ? defaultGroup : primaryGroup;
        return new UserImpl(uuid, username, group, calculator, defaultOptions);
    }

    public GroupImpl newGroup(String name) {
        return new GroupImpl(name, calculator, defaultOptions);
    }

    public TrackImpl newTrack(String name) {
        return new TrackImpl(name, eventBus, updatePrimaryOnTrack);
    }
}
