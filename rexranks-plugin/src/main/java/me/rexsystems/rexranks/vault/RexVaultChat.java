package me.rexsystems.rexranks.vault;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class RexVaultChat extends Chat {
    private final RexRanksPlugin plugin;

    public RexVaultChat(RexRanksPlugin plugin, Permission perms) {
        super(perms);
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
    public String getPlayerPrefix(String world, String player) {
        return getPlayerPrefix(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String getPlayerPrefix(String world, OfflinePlayer player) {
        return meta(player, world).getPrefix();
    }

    @Override
    public void setPlayerPrefix(String world, String player, String prefix) {
        setPlayerPrefix(world, Bukkit.getOfflinePlayer(player), prefix);
    }

    @Override
    public void setPlayerPrefix(String world, OfflinePlayer player, String prefix) {
        UserImpl user = (UserImpl) plugin.userManager().loadUser(player.getUniqueId(), player.getName()).join();
        user.data().addOrReplace(Node.prefix(100, prefix == null ? "" : prefix));
        plugin.userManager().saveUser(user);
    }

    @Override
    public String getPlayerSuffix(String world, String player) {
        return getPlayerSuffix(world, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public String getPlayerSuffix(String world, OfflinePlayer player) {
        return meta(player, world).getSuffix();
    }

    @Override
    public void setPlayerSuffix(String world, String player, String suffix) {
        setPlayerSuffix(world, Bukkit.getOfflinePlayer(player), suffix);
    }

    @Override
    public void setPlayerSuffix(String world, OfflinePlayer player, String suffix) {
        UserImpl user = (UserImpl) plugin.userManager().loadUser(player.getUniqueId(), player.getName()).join();
        user.data().addOrReplace(Node.suffix(100, suffix == null ? "" : suffix));
        plugin.userManager().saveUser(user);
    }

    @Override
    public String getGroupPrefix(String world, String group) {
        return groupMeta(group, world).getPrefix();
    }

    @Override
    public void setGroupPrefix(String world, String group, String prefix) {
        GroupImpl g = plugin.storage().getGroup(group).orElse(null);
        if (g == null) {
            return;
        }
        g.data().addOrReplace(Node.prefix(100, prefix == null ? "" : prefix));
        plugin.groupManager().saveGroup(g);
        plugin.invalidateAllCaches();
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        return groupMeta(group, world).getSuffix();
    }

    @Override
    public void setGroupSuffix(String world, String group, String suffix) {
        GroupImpl g = plugin.storage().getGroup(group).orElse(null);
        if (g == null) {
            return;
        }
        g.data().addOrReplace(Node.suffix(100, suffix == null ? "" : suffix));
        plugin.groupManager().saveGroup(g);
        plugin.invalidateAllCaches();
    }

    @Override
    public int getPlayerInfoInteger(String world, String player, String node, int defaultValue) {
        return parseInt(getPlayerInfoString(world, player, node, null), defaultValue);
    }

    @Override
    public void setPlayerInfoInteger(String world, String player, String node, int value) {
        setPlayerInfoString(world, player, node, Integer.toString(value));
    }

    @Override
    public double getPlayerInfoDouble(String world, String player, String node, double defaultValue) {
        try {
            String raw = getPlayerInfoString(world, player, node, null);
            return raw == null ? defaultValue : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setPlayerInfoDouble(String world, String player, String node, double value) {
        setPlayerInfoString(world, player, node, Double.toString(value));
    }

    @Override
    public boolean getPlayerInfoBoolean(String world, String player, String node, boolean defaultValue) {
        String raw = getPlayerInfoString(world, player, node, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw);
    }

    @Override
    public void setPlayerInfoBoolean(String world, String player, String node, boolean value) {
        setPlayerInfoString(world, player, node, Boolean.toString(value));
    }

    @Override
    public String getPlayerInfoString(String world, String player, String node, String defaultValue) {
        return meta(Bukkit.getOfflinePlayer(player), world).getMeta(node).orElse(defaultValue);
    }

    @Override
    public void setPlayerInfoString(String world, String player, String node, String value) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        UserImpl user = (UserImpl) plugin.userManager().loadUser(offline.getUniqueId(), offline.getName()).join();
        user.data().addOrReplace(Node.meta(node, value == null ? "" : value));
        plugin.userManager().saveUser(user);
    }

    @Override
    public int getGroupInfoInteger(String world, String group, String node, int defaultValue) {
        return parseInt(getGroupInfoString(world, group, node, null), defaultValue);
    }

    @Override
    public void setGroupInfoInteger(String world, String group, String node, int value) {
        setGroupInfoString(world, group, node, Integer.toString(value));
    }

    @Override
    public double getGroupInfoDouble(String world, String group, String node, double defaultValue) {
        try {
            String raw = getGroupInfoString(world, group, node, null);
            return raw == null ? defaultValue : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setGroupInfoDouble(String world, String group, String node, double value) {
        setGroupInfoString(world, group, node, Double.toString(value));
    }

    @Override
    public boolean getGroupInfoBoolean(String world, String group, String node, boolean defaultValue) {
        String raw = getGroupInfoString(world, group, node, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw);
    }

    @Override
    public void setGroupInfoBoolean(String world, String group, String node, boolean value) {
        setGroupInfoString(world, group, node, Boolean.toString(value));
    }

    @Override
    public String getGroupInfoString(String world, String group, String node, String defaultValue) {
        return groupMeta(group, world).getMeta(node).orElse(defaultValue);
    }

    @Override
    public void setGroupInfoString(String world, String group, String node, String value) {
        GroupImpl g = plugin.storage().getGroup(group).orElse(null);
        if (g == null) {
            return;
        }
        g.data().addOrReplace(Node.meta(node, value == null ? "" : value));
        plugin.groupManager().saveGroup(g);
        plugin.invalidateAllCaches();
    }

    private CachedMetaData meta(OfflinePlayer player, String world) {
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return empty();
        }
        return user.getCachedData().getMetaData(options(world));
    }

    private CachedMetaData groupMeta(String group, String world) {
        return plugin.storage().getGroup(group)
                .map(g -> g.getCachedData().getMetaData(options(world)))
                .orElseGet(this::empty);
    }

    private QueryOptions options(String world) {
        if (world == null || world.isBlank()) {
            return QueryOptions.defaultContextual();
        }
        return QueryOptions.contextual(plugin.contextService().staticContexts().mutableCopy().add("world", world).immutable());
    }

    private CachedMetaData empty() {
        return plugin.calculator().metaData(java.util.Set.of(), plugin.config().defaultGroup(), QueryOptions.DEFAULT);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
