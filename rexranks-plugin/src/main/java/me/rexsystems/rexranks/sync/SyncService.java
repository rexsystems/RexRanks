package me.rexsystems.rexranks.sync;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.event.DataReloadEvent;
import me.rexsystems.rexranks.storage.SqlStorage;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Cross-server synchronisation via database polling, Redis pub/sub, and/or
 * proxy plugin messaging.
 */
public final class SyncService {
    public static final String CHANNEL = "rexranks:sync";

    private final RexRanksPlugin plugin;
    private final AtomicLong lastPull = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private JedisPool redis;
    private Thread redisThread;
    private volatile JedisPubSub subscriber;

    public SyncService(RexRanksPlugin plugin) {
        this.plugin = plugin;
        try {
            this.lastPull.set(plugin.storage().maxUpdatedAt());
        } catch (Exception e) {
            this.lastPull.set(0);
        }
    }

    public void start() {
        if (!plugin.config().syncEnabled()) {
            return;
        }
        running.set(true);
        Set<String> methods = plugin.config().syncMethods();
        if (methods.contains("database")) {
            int seconds = plugin.config().syncPollSeconds();
            plugin.schedulers().runRepeatingAsync(() -> {
                if (running.get()) {
                    try {
                        pull();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Sync pull failed", e);
                    }
                }
            }, seconds * 20L, seconds * 20L);
        }
        if (methods.contains("redis")) {
            startRedis();
        }
        if (methods.contains("plugin-messaging") || methods.contains("pluginmessaging")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, (channel, player, message) -> {
                if (!running.get()) {
                    return;
                }
                plugin.schedulers().runAsync(this::pull);
            });
        }
        plugin.getLogger().info("Sync enabled (" + String.join(", ", methods) + ")");
    }

    public void stop() {
        running.set(false);
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (Exception ignored) {
            }
        }
        if (redisThread != null) {
            redisThread.interrupt();
        }
        if (redis != null) {
            redis.close();
            redis = null;
        }
    }

    public void push(String reason) {
        try {
            lastPull.set(Math.max(lastPull.get(), plugin.storage().maxUpdatedAt()));
        } catch (Exception ignored) {
        }
        if (!plugin.config().syncEnabled()) {
            return;
        }
        Set<String> methods = plugin.config().syncMethods();
        if (methods.contains("redis") && redis != null) {
            try (Jedis jedis = redis.getResource()) {
                jedis.publish(plugin.config().redisChannel(), reason == null ? "change" : reason);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Redis publish failed", e);
            }
        }
        if (methods.contains("plugin-messaging") || methods.contains("pluginmessaging")) {
            byte[] payload = (reason == null ? "change" : reason).getBytes(StandardCharsets.UTF_8);
            plugin.getServer().getOnlinePlayers().stream().findFirst().ifPresent(player -> {
                try {
                    player.sendPluginMessage(plugin, CHANNEL, payload);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Plugin-messaging push failed: " + e.getMessage());
                }
            });
        }
    }

    public int pull() {
        if (!(plugin.storage() instanceof SqlStorage sql)) {
            return 0;
        }
        long since = lastPull.get();
        try {
            int changes = sql.pullRemoteChanges(since);
            lastPull.set(Math.max(since, sql.maxUpdatedAt()));
            if (changes > 0) {
                plugin.invalidateAllCaches();
                plugin.eventBus().post(new DataReloadEvent("sync"));
            }
            return changes;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to pull remote changes", e);
            return 0;
        }
    }

    private void startRedis() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(2);
            String password = plugin.config().redisPassword();
            if (password == null || password.isBlank()) {
                redis = new JedisPool(poolConfig, plugin.config().redisHost(), plugin.config().redisPort(),
                        4000, plugin.config().redisSsl());
            } else {
                redis = new JedisPool(poolConfig, plugin.config().redisHost(), plugin.config().redisPort(),
                        4000, password, plugin.config().redisSsl());
            }
            subscriber = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if (running.get()) {
                        plugin.schedulers().runAsync(SyncService.this::pull);
                    }
                }
            };
            redisThread = new Thread(() -> {
                try (Jedis jedis = redis.getResource()) {
                    jedis.subscribe(subscriber, plugin.config().redisChannel());
                } catch (Exception e) {
                    if (running.get()) {
                        plugin.getLogger().log(Level.WARNING, "Redis subscribe ended", e);
                    }
                }
            }, "RexRanks-Redis");
            redisThread.setDaemon(true);
            redisThread.start();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to start Redis sync", e);
        }
    }
}
