package me.rexsystems.rexranks;

import me.rexsystems.rexranks.api.RexRanks;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.api.group.GroupManager;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.track.TrackManager;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.api.user.UserManager;
import me.rexsystems.rexranks.service.ContextService;
import me.rexsystems.rexranks.sync.SyncService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class RexRanksApiImpl implements RexRanks {
    private final RexRanksPlugin plugin;

    public RexRanksApiImpl(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public String getServerName() {
        return plugin.config().serverName();
    }

    @Override
    public String getDefaultGroup() {
        return plugin.config().defaultGroup();
    }

    @Override
    public UserManager getUserManager() {
        return plugin.userManager();
    }

    @Override
    public GroupManager getGroupManager() {
        return plugin.groupManager();
    }

    @Override
    public TrackManager getTrackManager() {
        return plugin.trackManager();
    }

    @Override
    public EventBus getEventBus() {
        return plugin.eventBus();
    }

    @Override
    public ImmutableContextSet getStaticContexts() {
        return plugin.contextService().staticContexts();
    }

    @Override
    public QueryOptions getQueryOptionsFor(UUID uniqueId) {
        return plugin.contextService().optionsFor(uniqueId);
    }

    @Override
    public Tristate checkPermission(UUID uniqueId, String permission) {
        return plugin.userManager().getUser(uniqueId)
                .map(user -> user.checkPermission(permission, getQueryOptionsFor(uniqueId)))
                .orElse(Tristate.UNDEFINED);
    }

    @Override
    public CompletableFuture<Void> loadAll() {
        return CompletableFuture.runAsync(() -> {
            try {
                plugin.storage().loadAll();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, plugin.schedulers().async());
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        return CompletableFuture.runAsync(plugin::saveAllNow, plugin.schedulers().async());
    }

    @Override
    public void requestSync() {
        SyncService sync = plugin.syncService();
        if (sync != null) {
            sync.push("manual");
        }
    }

    @Override
    public Executor getSyncExecutor() {
        return plugin.schedulers().sync();
    }

    @Override
    public Executor getAsyncExecutor() {
        return plugin.schedulers().async();
    }
}
