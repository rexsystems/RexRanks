package me.rexsystems.rexranks.vault;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class RexVaultPermission extends Permission {
    private final RexRanksPlugin plugin;

    public RexVaultPermission(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "RexRanks";
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    @Override
    public boolean playerHas(String world, String player, String permission) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return playerHas(world, offline, permission);
    }

    @Override
    public boolean playerHas(String world, OfflinePlayer player, String permission) {
        return check(player.getUniqueId(), world, permission).asBoolean();
    }

    @Override
    public boolean playerAdd(String world, String player, String permission) {
        return playerAdd(world, Bukkit.getOfflinePlayer(player), permission);
    }

    @Override
    public boolean playerAdd(String world, OfflinePlayer player, String permission) {
        UserImpl user = load(player);
        if (user == null) {
            return false;
        }
        Node node = Node.permission(permission, true);
        if (world != null && !world.isBlank()) {
            node = node.toBuilder().contexts(me.rexsystems.rexranks.api.context.ImmutableContextSet.of("world", world)).build();
        }
        user.data().addOrReplace(node);
        plugin.userManager().saveUser(user);
        return true;
    }

    @Override
    public boolean playerRemove(String world, String player, String permission) {
        return playerRemove(world, Bukkit.getOfflinePlayer(player), permission);
    }

    @Override
    public boolean playerRemove(String world, OfflinePlayer player, String permission) {
        UserImpl user = load(player);
        if (user == null) {
            return false;
        }
        String worldName = world;
        int removed = user.data().removeIf(n -> n.type() == NodeType.PERMISSION
                && n.key().equalsIgnoreCase(permission)
                && (worldName == null || n.contexts().getAny("world").map(worldName::equalsIgnoreCase).orElse(false)));
        if (removed > 0) {
            plugin.userManager().saveUser(user);
        }
        return removed > 0;
    }

    @Override
    public boolean groupHas(String world, String group, String permission) {
        return plugin.storage().getGroup(group)
                .map(g -> g.getCachedData().getPermissionData(options(world)).check(permission).asBoolean())
                .orElse(false);
    }

    @Override
    public boolean groupAdd(String world, String group, String permission) {
        GroupImpl g = plugin.storage().getGroup(group).orElse(null);
        if (g == null) {
            return false;
        }
        Node node = Node.permission(permission, true);
        if (world != null && !world.isBlank()) {
            node = node.toBuilder().contexts(me.rexsystems.rexranks.api.context.ImmutableContextSet.of("world", world)).build();
        }
        g.data().addOrReplace(node);
        plugin.groupManager().saveGroup(g);
        plugin.invalidateAllCaches();
        return true;
    }

    @Override
    public boolean groupRemove(String world, String group, String permission) {
        GroupImpl g = plugin.storage().getGroup(group).orElse(null);
        if (g == null) {
            return false;
        }
        String worldName = world;
        int removed = g.data().removeIf(n -> n.type() == NodeType.PERMISSION
                && n.key().equalsIgnoreCase(permission)
                && (worldName == null || n.contexts().getAny("world").map(worldName::equalsIgnoreCase).orElse(false)));
        if (removed > 0) {
            plugin.groupManager().saveGroup(g);
            plugin.invalidateAllCaches();
        }
        return removed > 0;
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        return playerInGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerInGroup(String world, OfflinePlayer player, String group) {
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.getPrimaryGroup().equalsIgnoreCase(group)) {
            return true;
        }
        return user.getCachedData().getPermissionData(options(world)).getGroups().stream()
                .anyMatch(g -> g.equalsIgnoreCase(group));
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        return playerAddGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerAddGroup(String world, OfflinePlayer player, String group) {
        UserImpl user = load(player);
        if (user == null || plugin.storage().getGroup(group).isEmpty()) {
            return false;
        }
        user.data().addOrReplace(Node.inheritance(group));
        plugin.userManager().saveUser(user);
        return true;
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        return playerRemoveGroup(world, Bukkit.getOfflinePlayer(player), group);
    }

    @Override
    public boolean playerRemoveGroup(String world, OfflinePlayer player, String group) {
        UserImpl user = load(player);
        if (user == null) {
            return false;
        }
        int removed = user.data().removeIf(n -> n.type() == NodeType.INHERITANCE && n.groupName().equalsIgnoreCase(group));
        if (removed > 0) {
            plugin.userManager().saveUser(user);
        }
        return removed > 0;
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        return getPlayerGroups(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String[] getPlayerGroups(String world, OfflinePlayer player) {
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return new String[0];
        }
        return user.getCachedData().getPermissionData(options(world)).getGroups().toArray(String[]::new);
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        return getPrimaryGroup(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String getPrimaryGroup(String world, OfflinePlayer player) {
        return plugin.userManager().getUser(player.getUniqueId())
                .map(User::getPrimaryGroup)
                .orElse(plugin.config().defaultGroup());
    }

    @Override
    public String[] getGroups() {
        return plugin.storage().getLoadedGroups().stream().map(GroupImpl::getName).toArray(String[]::new);
    }

    private UserImpl load(OfflinePlayer player) {
        return (UserImpl) plugin.userManager().loadUser(player.getUniqueId(), player.getName()).join();
    }

    private QueryOptions options(String world) {
        if (world == null || world.isBlank()) {
            return plugin.contextService().optionsFor(new UUID(0, 0));
        }
        return QueryOptions.contextual(plugin.contextService().staticContexts().mutableCopy()
                .add("world", world).immutable());
    }

    private Tristate check(UUID uuid, String world, String permission) {
        User user = plugin.userManager().getUser(uuid).orElse(null);
        if (user == null) {
            return Tristate.UNDEFINED;
        }
        return user.checkPermission(permission, options(world));
    }
}
