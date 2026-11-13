package me.rexsystems.rexranks.listener;

import me.rexsystems.rexranks.RexRanksPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Reflectively replaces the {@link PermissibleBase} on a CraftPlayer.
 * Paper uses an unversioned {@code org.bukkit.craftbukkit.entity.CraftHumanEntity}
 * field named {@code perm}.
 */
public final class PermissibleInjector {
    private final RexRanksPlugin plugin;
    private volatile Field permField;
    private volatile boolean unavailable;

    public PermissibleInjector(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    public void inject(Player player) {
        if (unavailable) {
            return;
        }
        try {
            Field field = field(player);
            if (field == null) {
                return;
            }
            Object current = field.get(player);
            if (current instanceof RexPermissible) {
                return;
            }
            PermissibleBase previous = current instanceof PermissibleBase base ? base : null;
            RexPermissible replacement = new RexPermissible(player, plugin, previous);
            field.set(player, replacement);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to inject permissible for " + player.getName()
                    + ". Permission checks will fall back to Vault / attachments.", e);
            unavailable = true;
        }
    }

    public void uninject(Player player) {
        if (unavailable || permField == null) {
            return;
        }
        try {
            Object current = permField.get(player);
            if (current instanceof RexPermissible rex && rex.previous() != null) {
                permField.set(player, rex.previous());
            }
        } catch (Exception ignored) {
        }
    }

    private Field field(Player player) {
        Field cached = permField;
        if (cached != null) {
            return cached;
        }
        Class<?> type = player.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("perm");
                if (PermissibleBase.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    permField = field;
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
            type = type.getSuperclass();
        }
        plugin.getLogger().warning("Could not find CraftHumanEntity.perm — Bukkit injection is unavailable on this server.");
        unavailable = true;
        return null;
    }
}
