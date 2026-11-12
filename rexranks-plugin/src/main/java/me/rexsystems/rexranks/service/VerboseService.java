package me.rexsystems.rexranks.service;

import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.config.Messages;
import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VerboseService {
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private volatile boolean console;
    private Messages messages;

    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    public boolean toggle(CommandSender sender) {
        if (sender instanceof Player player) {
            if (players.contains(player.getUniqueId())) {
                players.remove(player.getUniqueId());
                return false;
            }
            players.add(player.getUniqueId());
            return true;
        }
        console = !console;
        return console;
    }

    public boolean isEnabled(CommandSender sender) {
        if (sender instanceof Player player) {
            return players.contains(player.getUniqueId());
        }
        return console;
    }

    public void record(String username, String permission, Tristate result, String origin) {
        if (players.isEmpty() && !console) {
            return;
        }
        String value = switch (result) {
            case TRUE -> "<green>true";
            case FALSE -> "<red>false";
            case UNDEFINED -> "<dark_gray>undefined";
        };
        if (messages == null) {
            return;
        }
        var component = messages.component("check.verbose-line", Map.of(
                "name", username,
                "node", permission,
                "value", value,
                "origin", origin == null ? "" : origin
        ));
        if (console) {
            Bukkit.getConsoleSender().sendMessage(component);
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(component);
            }
        }
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }
}
