package me.rexsystems.rexranks.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.data.CachedPermissionData;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RexRanksExpansion extends PlaceholderExpansion {
    private final RexRanksPlugin plugin;

    public RexRanksExpansion(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rexranks";
    }

    @Override
    public @NotNull String getAuthor() {
        return "RexSystems";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        User user = plugin.userManager().getUser(player.getUniqueId()).orElse(null);
        if (user == null) {
            return "";
        }
        QueryOptions options = plugin.contextService().optionsFor(player.getUniqueId());
        CachedMetaData meta = user.getCachedData().getMetaData(options);
        CachedPermissionData perms = user.getCachedData().getPermissionData(options);
        String key = params.toLowerCase();
        return switch (key) {
            case "prefix" -> meta.getPrefix();
            case "suffix" -> meta.getSuffix();
            case "group", "primary", "primary_group", "primarygroup" -> user.getPrimaryGroup();
            case "display", "displayname", "display_name" -> {
                String display = meta.getDisplayName();
                yield display.isEmpty() ? user.getPrimaryGroup() : display;
            }
            case "groups" -> String.join(", ", perms.getGroups());
            case "weight" -> Integer.toString(meta.getWeight());
            case "username" -> user.getUsername();
            default -> {
                if (key.startsWith("meta_")) {
                    yield meta.getMeta(params.substring(5)).orElse("");
                }
                if (key.startsWith("has_permission_") || key.startsWith("hasperm_")) {
                    String node = key.startsWith("has_permission_") ? params.substring("has_permission_".length()) : params.substring("hasperm_".length());
                    yield Boolean.toString(perms.checkBoolean(node));
                }
                if (key.startsWith("in_group_") || key.startsWith("ingroup_")) {
                    String group = key.startsWith("in_group_") ? params.substring("in_group_".length()) : params.substring("ingroup_".length());
                    yield Boolean.toString(perms.getGroups().stream().anyMatch(g -> g.equalsIgnoreCase(group))
                            || user.getPrimaryGroup().equalsIgnoreCase(group));
                }
                if (key.startsWith("expires_")) {
                    String node = params.substring("expires_".length());
                    yield user.data().toSet().stream()
                            .filter(n -> n.key().equalsIgnoreCase(node) && n.hasExpiry())
                            .map(n -> me.rexsystems.rexranks.engine.DurationParser.formatRemaining(n.expiryUnix()))
                            .findFirst()
                            .orElse("");
                }
                yield null;
            }
        };
    }
}
