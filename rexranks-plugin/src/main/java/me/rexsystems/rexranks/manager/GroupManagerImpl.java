package me.rexsystems.rexranks.manager;

import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.group.GroupManager;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public final class GroupManagerImpl implements GroupManager {
    private static final Pattern NAME = Pattern.compile("^[a-z0-9_\\-]{1,64}$");
    private final Storage storage;
    private final StorageContext context;
    private final Executor async;

    public GroupManagerImpl(Storage storage, StorageContext context, Executor async) {
        this.storage = storage;
        this.context = context;
        this.async = async;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    @Override
    public CompletableFuture<Group> createAndLoadGroup(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String id = name.toLowerCase(Locale.ROOT);
            if (!isValidName(id)) {
                throw new IllegalArgumentException("Invalid group name: " + name);
            }
            Optional<GroupImpl> existing = storage.getGroup(id);
            if (existing.isPresent()) {
                return existing.get();
            }
            GroupImpl group = context.newGroup(id);
            try {
                storage.saveGroup(group);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create group " + id, e);
            }
            return group;
        }, async);
    }

    @Override
    public CompletableFuture<Optional<Group>> loadGroup(String name) {
        return CompletableFuture.supplyAsync(() -> storage.getGroup(name).map(g -> g), async);
    }

    @Override
    public Optional<Group> getGroup(String name) {
        return storage.getGroup(name).map(g -> g);
    }

    @Override
    public Collection<Group> getLoadedGroups() {
        return storage.getLoadedGroups().stream().map(g -> (Group) g).toList();
    }

    @Override
    public CompletableFuture<Void> saveGroup(Group group) {
        return CompletableFuture.runAsync(() -> {
            try {
                GroupImpl impl = (GroupImpl) group;
                impl.touch();
                storage.saveGroup(impl);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save group " + group.getName(), e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<Boolean> deleteGroup(Group group) {
        return CompletableFuture.supplyAsync(() -> {
            if (group.getName().equalsIgnoreCase(context.defaultGroup())) {
                throw new IllegalStateException("The default group cannot be deleted");
            }
            try {
                return storage.deleteGroup(group.getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<Void> loadAllGroups() {
        return CompletableFuture.runAsync(() -> {
            try {
                storage.loadAll();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, async);
    }
}
