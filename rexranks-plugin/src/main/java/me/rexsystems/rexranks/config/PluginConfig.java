package me.rexsystems.rexranks.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class PluginConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    public String serverName() {
        return config.getString("server-name", "global");
    }

    public String defaultGroup() {
        return config.getString("default-group", "default").toLowerCase(Locale.ROOT);
    }

    public boolean updatePrimaryOnTrack() {
        return config.getBoolean("tracks.update-primary-group", true);
    }

    public String storageType() {
        return config.getString("storage.type", "h2").toLowerCase(Locale.ROOT);
    }

    public String mysqlHost() { return config.getString("storage.mysql.host", "127.0.0.1"); }
    public int mysqlPort() { return config.getInt("storage.mysql.port", 3306); }
    public String mysqlDatabase() { return config.getString("storage.mysql.database", "rexranks"); }
    public String mysqlUsername() { return config.getString("storage.mysql.username", "rexranks"); }
    public String mysqlPassword() { return config.getString("storage.mysql.password", ""); }
    public int mysqlPoolSize() { return config.getInt("storage.mysql.pool-size", 8); }
    public String mysqlProperties() { return config.getString("storage.mysql.properties", ""); }

    public boolean syncEnabled() { return config.getBoolean("sync.enabled", false); }
    public Set<String> syncMethods() {
        String raw = config.getString("sync.method", "database");
        return Set.of(raw.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
    }
    public int syncPollSeconds() { return Math.max(2, config.getInt("sync.poll-seconds", 5)); }
    public String redisHost() { return config.getString("sync.redis.host", "127.0.0.1"); }
    public int redisPort() { return config.getInt("sync.redis.port", 6379); }
    public String redisPassword() { return config.getString("sync.redis.password", ""); }
    public boolean redisSsl() { return config.getBoolean("sync.redis.ssl", false); }
    public String redisChannel() { return config.getString("sync.redis.channel", "rexranks:sync"); }

    public boolean contextWorld() { return config.getBoolean("contexts.include-world", true); }
    public boolean contextServer() { return config.getBoolean("contexts.include-server", true); }
    public boolean contextDimension() { return config.getBoolean("contexts.include-dimension", false); }
    public boolean contextGamemode() { return config.getBoolean("contexts.include-gamemode", false); }

    public boolean opHasAll() { return config.getBoolean("bukkit.op-has-all-permissions", true); }
    public boolean applyBukkitDefaults() { return config.getBoolean("bukkit.apply-bukkit-defaults", true); }

    public boolean editorEnabled() { return config.getBoolean("editor.enabled", true); }
    public String editorMode() { return config.getString("editor.mode", "integrated").toLowerCase(Locale.ROOT); }
    public String editorHost() { return config.getString("editor.host", "0.0.0.0"); }
    public int editorPort() { return config.getInt("editor.port", 7780); }
    public String editorPublicUrl() { return config.getString("editor.public-url", ""); }
    public int editorTokenMinutes() { return Math.max(5, config.getInt("editor.token-lifetime-minutes", 120)); }
    public String editorRemoteUrl() { return config.getString("editor.remote-url", "http://127.0.0.1:7781"); }

    public boolean updateChecker() { return config.getBoolean("update-checker.enabled", true); }

    public List<String> knownNodes() {
        return config.getStringList("known-nodes").stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toList());
    }
}
