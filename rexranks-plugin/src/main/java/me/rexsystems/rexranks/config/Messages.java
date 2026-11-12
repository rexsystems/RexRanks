package me.rexsystems.rexranks.config;

import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Messages {
    private final Plugin plugin;
    private FileConfiguration config;
    private String prefix = "&7[&6RexRanks&7] ";

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.prefix = config.getString("prefix", "&7[&6RexRanks&7] ");
    }

    public String raw(String key) {
        return config.getString(key, key);
    }

    public String raw(String key, String def) {
        return config.getString(key, def);
    }

    public Component component(String key, Map<String, String> placeholders) {
        String text = raw(key).replace("%prefix%", prefix).replace("{prefix}", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                text = text.replace("{" + entry.getKey() + "}", value);
                text = text.replace("%" + entry.getKey() + "%", value);
            }
        }
        return Text.parse(text);
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, String k1, String v1) {
        return component(key, Map.of(k1, v1));
    }

    public Component component(String key, String k1, String v1, String k2, String v2) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return component(key, map);
    }

    public Component component(String key, String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return component(key, map);
    }

    public void send(Audience audience, String key) {
        audience.sendMessage(component(key));
    }

    public void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.sendMessage(component(key, placeholders));
    }

    public void send(Audience audience, String key, String k1, String v1) {
        audience.sendMessage(component(key, k1, v1));
    }

    public void send(Audience audience, String key, String k1, String v1, String k2, String v2) {
        audience.sendMessage(component(key, k1, v1, k2, v2));
    }

    public void send(Audience audience, String key, String k1, String v1, String k2, String v2, String k3, String v3) {
        audience.sendMessage(component(key, k1, v1, k2, v2, k3, v3));
    }

    public String prefix() {
        return prefix;
    }

    public void mergeMissing() throws IOException {
        // Keys present in the jar but missing on disk are already handled by defaults;
        // we just ensure the file exists.
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }
}
