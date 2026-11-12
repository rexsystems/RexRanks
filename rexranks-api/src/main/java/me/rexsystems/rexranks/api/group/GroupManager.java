package me.rexsystems.rexranks.api.group;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loads, creates, and saves groups.
 */
public interface GroupManager {
    CompletableFuture<Group> createAndLoadGroup(String name);

    CompletableFuture<Optional<Group>> loadGroup(String name);

    Optional<Group> getGroup(String name);

    Collection<Group> getLoadedGroups();

    CompletableFuture<Void> saveGroup(Group group);

    CompletableFuture<Boolean> deleteGroup(Group group);

    CompletableFuture<Void> loadAllGroups();

    default Group getDefaultGroup() {
        return getGroup("default").orElseThrow(() -> new IllegalStateException("Default group is not loaded"));
    }
}
