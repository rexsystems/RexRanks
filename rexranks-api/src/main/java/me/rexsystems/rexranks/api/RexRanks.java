package me.rexsystems.rexranks.api;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.api.group.GroupManager;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.track.TrackManager;
import me.rexsystems.rexranks.api.user.UserManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Entry point for the RexRanks API.
 *
 * <p>Obtain an instance with {@link RexRanksProvider#get()} once the plugin is enabled,
 * or through Bukkit's {@code ServicesManager}.
 *
 * <pre>{@code
 * RexRanks api = RexRanksProvider.get();
 *
 * api.getUserManager().loadUser(player.getUniqueId()).thenAccept(user -> {
 *     user.data().add(Node.permission("essentials.fly"));
 *     user.data().add(Node.prefix(100, "<gradient:#ff6b6b:#ffd93d>[Admin] "));
 *     api.getUserManager().saveUser(user);
 * });
 *
 * api.getEventBus().subscribe(myPlugin, UserPromoteEvent.class, event -> {
 *     // ...
 * });
 * }</pre>
 */
public interface RexRanks {
    static RexRanks get() {
        return RexRanksProvider.get();
    }

    static boolean isAvailable() {
        return RexRanksProvider.isLoaded();
    }

    String getVersion();

    String getServerName();

    String getDefaultGroup();

    UserManager getUserManager();

    GroupManager getGroupManager();

    TrackManager getTrackManager();

    EventBus getEventBus();

    /**
     * The context set currently applicable to this server (server name, and
     * optionally nothing else — world is per-player).
     */
    ImmutableContextSet getStaticContexts();

    QueryOptions getQueryOptionsFor(UUID uniqueId);

    Tristate checkPermission(UUID uniqueId, String permission);

    CompletableFuture<Void> loadAll();

    CompletableFuture<Void> saveAll();

    /**
     * Push a change notification to other servers on the network.
     */
    void requestSync();

    Executor getSyncExecutor();

    Executor getAsyncExecutor();
}
