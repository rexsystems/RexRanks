package me.rexsystems.rexranks.service;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.context.MutableContextSet;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.config.PluginConfig;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Function;

public final class ContextService {
    private final PluginConfig config;
    private Function<UUID, Player> playerLookup = id -> null;

    public ContextService(PluginConfig config) {
        this.config = config;
    }

    public void setPlayerLookup(Function<UUID, Player> playerLookup) {
        this.playerLookup = playerLookup;
    }

    public ImmutableContextSet staticContexts() {
        ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
        if (config.contextServer()) {
            builder.add("server", config.serverName());
        }
        return builder.build();
    }

    public ImmutableContextSet forPlayer(Player player) {
        MutableContextSet builder = staticContexts().mutableCopy();
        if (config.contextWorld() && player.getWorld() != null) {
            builder.add("world", player.getWorld().getName());
        }
        if (config.contextDimension() && player.getWorld() != null) {
            builder.add("dimension", player.getWorld().getEnvironment().name());
        }
        if (config.contextGamemode()) {
            builder.add("gamemode", player.getGameMode().name());
        }
        return builder.immutable();
    }

    public QueryOptions optionsFor(UUID uuid) {
        Player player = playerLookup.apply(uuid);
        if (player != null && player.isOnline()) {
            return QueryOptions.contextual(forPlayer(player));
        }
        return QueryOptions.contextual(staticContexts());
    }
}
