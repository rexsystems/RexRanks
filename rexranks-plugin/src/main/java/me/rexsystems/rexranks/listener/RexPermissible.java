package me.rexsystems.rexranks.listener;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.user.User;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;

import java.util.HashSet;
import java.util.Set;

/**
 * Replaces Bukkit's {@link PermissibleBase} so {@code Player#hasPermission}
 * is answered by RexRanks.
 */
public final class RexPermissible extends PermissibleBase {
    private final Player player;
    private final RexRanksPlugin plugin;
    private final PermissibleBase previous;

    public RexPermissible(Player player, RexRanksPlugin plugin, PermissibleBase previous) {
        super(player);
        this.player = player;
        this.plugin = plugin;
        this.previous = previous;
    }

    public PermissibleBase previous() {
        return previous;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return check(name) != Tristate.UNDEFINED;
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return isPermissionSet(perm.getName());
    }

    @Override
    public boolean hasPermission(String name) {
        Tristate result = check(name);
        String origin = result.isDefined() ? "rexranks" : fallbackOrigin(name);
        plugin.verbose().record(player.getName(), name, result.isDefined() ? result : Tristate.of(fallback(name)), origin);
        if (result.isDefined()) {
            return result.asBoolean();
        }
        return fallback(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return hasPermission(perm.getName());
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        Set<PermissionAttachmentInfo> set = new HashSet<>();
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return previous != null ? previous.getEffectivePermissions() : set;
        }
        QueryOptions options = plugin.contextService().optionsFor(player.getUniqueId());
        user.getCachedData().getPermissionData(options).getPermissionMap().forEach((node, tristate) -> {
            if (tristate.isDefined()) {
                set.add(new PermissionAttachmentInfo(player, node, null, tristate.asBoolean()));
            }
        });
        return set;
    }

    private Tristate check(String name) {
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return Tristate.UNDEFINED;
        }
        return user.checkPermission(name, plugin.contextService().optionsFor(player.getUniqueId()));
    }

    private boolean fallback(String name) {
        if (plugin.config().opHasAll() && player.isOp()) {
            return true;
        }
        if (plugin.config().applyBukkitDefaults() && previous != null) {
            try {
                return previous.hasPermission(name);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private String fallbackOrigin(String name) {
        if (plugin.config().opHasAll() && player.isOp()) {
            return "op";
        }
        return "bukkit-default";
    }

    public ServerOperator operator() {
        return player;
    }
}
