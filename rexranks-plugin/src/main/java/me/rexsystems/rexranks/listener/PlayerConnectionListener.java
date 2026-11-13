package me.rexsystems.rexranks.listener;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.model.UserImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerConnectionListener implements Listener {
    private final RexRanksPlugin plugin;
    private final PermissibleInjector injector;

    public PlayerConnectionListener(RexRanksPlugin plugin, PermissibleInjector injector) {
        this.plugin = plugin;
        this.injector = injector;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            plugin.userManager().loadUser(event.getUniqueId(), event.getName()).join();
            plugin.userManager().remember(event.getName(), event.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load user " + event.getName() + " on login: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.schedulers().runOnEntity(player, () -> {
            UserImpl user = (UserImpl) plugin.userManager().getUser(player.getUniqueId()).orElse(null);
            if (user != null) {
                if (!player.getName().equals(user.getUsername())) {
                    user.setUsername(player.getName());
                    plugin.userManager().saveUser(user);
                }
                plugin.userManager().remember(player.getName(), player.getUniqueId());
            }
            injector.inject(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.verbose().remove(event.getPlayer().getUniqueId());
        injector.uninject(event.getPlayer());
    }

    public void expireTemporaryNodes() {
        plugin.storage().getLoadedUsers().forEach(user -> {
            int removed = user.data().removeIf(Node::expired);
            if (removed > 0) {
                plugin.userManager().saveUser(user);
                plugin.eventBus().post(new me.rexsystems.rexranks.api.event.NodeExpireEvent(user, null, null));
            }
        });
        plugin.storage().getLoadedGroups().forEach(group -> {
            int removed = group.data().removeIf(Node::expired);
            if (removed > 0) {
                plugin.groupManager().saveGroup(group);
                plugin.eventBus().post(new me.rexsystems.rexranks.api.event.NodeExpireEvent(null, group, null));
                plugin.invalidateAllCaches();
            }
        });
    }
}
