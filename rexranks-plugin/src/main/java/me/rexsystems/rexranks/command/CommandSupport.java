package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.engine.DurationParser;
import me.rexsystems.rexranks.engine.NodeShorthand;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandSupport {
    private final RexRanksPlugin plugin;

    public CommandSupport(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    public RexRanksPlugin plugin() {
        return plugin;
    }

    public boolean permit(CommandSender sender, String node) {
        if (sender.hasPermission("rexranks.admin") || sender.hasPermission("rexranks.*") || sender.isOp()) {
            return true;
        }
        if (sender.hasPermission("rexranks." + node) || sender.hasPermission("rexranks." + node + ".*")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    public boolean permitSilent(CommandSender sender, String node) {
        return sender.hasPermission("rexranks.admin")
                || sender.hasPermission("rexranks.*")
                || sender.isOp()
                || sender.hasPermission("rexranks." + node);
    }

    public void usage(CommandSender sender, String usage) {
        plugin.messages().send(sender, "usage", "usage", usage);
    }

    public CompletableFuture<Optional<UserImpl>> resolveUser(CommandSender sender, String name) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return plugin.userManager().loadUser(online.getUniqueId(), online.getName()).thenApply(u -> Optional.of((UserImpl) u));
        }
        try {
            UUID uuid = UUID.fromString(name);
            return plugin.userManager().loadUser(uuid).thenApply(u -> Optional.of((UserImpl) u));
        } catch (IllegalArgumentException ignored) {
        }
        return plugin.userManager().lookupUniqueId(name).thenCompose(found -> {
            if (found.isPresent()) {
                return plugin.userManager().loadUser(found.get(), name).thenApply(u -> Optional.of((UserImpl) u));
            }
            if (!Bukkit.getOnlineMode()) {
                UUID offline = plugin.userManager().offlineUuid(name);
                return plugin.userManager().loadUser(offline, name).thenApply(u -> Optional.of((UserImpl) u));
            }
            // Last resort: create a record with the offline-style UUID so staff can still assign nodes
            // before the player has joined. Their real UUID is merged on first join if online-mode is on
            // and the name matches — see PlayerConnectionListener.
            UUID offline = plugin.userManager().offlineUuid(name);
            return plugin.userManager().loadUser(offline, name).thenApply(u -> Optional.of((UserImpl) u));
        });
    }

    public void withUser(CommandSender sender, String name, Consumer<UserImpl> action) {
        resolveUser(sender, name).whenComplete((opt, error) -> plugin.schedulers().runSync(() -> {
            if (error != null) {
                plugin.messages().send(sender, "player-not-found", "player", name);
                plugin.getLogger().warning("User resolve failed for " + name + ": " + error.getMessage());
                return;
            }
            if (opt.isEmpty()) {
                plugin.messages().send(sender, "player-not-found", "player", name);
                return;
            }
            action.accept(opt.get());
        }));
    }

    public Optional<GroupImpl> resolveGroup(CommandSender sender, String name, boolean createIfMissing) {
        Optional<GroupImpl> existing = plugin.storage().getGroup(name);
        if (existing.isPresent()) {
            return existing;
        }
        if (!createIfMissing) {
            plugin.messages().send(sender, "group-not-found", "group", name);
            return Optional.empty();
        }
        try {
            return Optional.of((GroupImpl) plugin.groupManager().createAndLoadGroup(name).join());
        } catch (RuntimeException e) {
            plugin.messages().send(sender, "group-not-found", "group", name);
            return Optional.empty();
        }
    }

    public void saveUser(UserImpl user) {
        plugin.userManager().saveUser(user).whenComplete((v, e) -> {
            if (e != null) {
                plugin.getLogger().warning("Failed to save user " + user.getUsername() + ": " + e.getMessage());
            } else {
                plugin.notifyChange("user:" + user.getUniqueId());
            }
        });
    }

    public void saveGroup(GroupImpl group) {
        plugin.groupManager().saveGroup(group).whenComplete((v, e) -> {
            if (e != null) {
                plugin.getLogger().warning("Failed to save group " + group.getName() + ": " + e.getMessage());
            } else {
                plugin.notifyChange("group:" + group.getName());
                plugin.invalidateAllCaches();
            }
        });
    }

    public Component formatNode(Node node) {
        String shorthand = NodeShorthand.toShorthand(node);
        String colour = node.negated() ? "<red>" : "<gray>";
        StringBuilder extra = new StringBuilder();
        if (node.hasExpiry()) {
            extra.append(" <dark_gray>(").append(DurationParser.formatRemaining(node.expiryUnix())).append(")");
        }
        if (!node.contexts().isEmpty()) {
            extra.append(" <dark_gray>").append(node.contexts());
        }
        Component component = Text.parse(colour + shorthand + extra);
        return component
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>Click to suggest unset")))
                .clickEvent(ClickEvent.suggestCommand("/rr "));
    }

    public void sendNodeList(CommandSender sender, String title, Iterable<Node> nodes) {
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>" + title));
        boolean any = false;
        for (Node node : nodes) {
            any = true;
            sender.sendMessage(Component.text("  - ").color(NamedTextColor.DARK_GRAY).append(formatNode(node)));
        }
        if (!any) {
            sender.sendMessage(Text.parse("  <dark_gray>(no nodes)"));
        }
    }

    public String tristate(Tristate value) {
        return switch (value) {
            case TRUE -> plugin.messages().raw("true", "&atrue");
            case FALSE -> plugin.messages().raw("false", "&cfalse");
            case UNDEFINED -> plugin.messages().raw("undefined", "&8undefined");
        };
    }

    public String extra(Node node) {
        StringBuilder sb = new StringBuilder();
        if (node.hasExpiry()) {
            sb.append(" ").append(Text.plain(plugin.messages().component("expiry-extra", "duration", DurationParser.formatRemaining(node.expiryUnix()))));
        }
        if (!node.contexts().isEmpty()) {
            sb.append(" ").append(node.contexts());
        }
        return sb.toString();
    }

    public List<String> completePlayers(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                names.add(player.getName());
            }
        }
        plugin.storage().uuidCache().keySet().stream()
                .filter(n -> n.startsWith(p) && names.stream().noneMatch(x -> x.equalsIgnoreCase(n)))
                .limit(30)
                .forEach(names::add);
        return names;
    }

    public List<String> completeGroups(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return plugin.storage().getLoadedGroups().stream()
                .map(g -> g.getName())
                .filter(n -> n.startsWith(p))
                .sorted()
                .toList();
    }

    public List<String> completeTracks(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return plugin.storage().getLoadedTracks().stream()
                .map(t -> t.getName())
                .filter(n -> n.startsWith(p))
                .sorted()
                .toList();
    }

    public List<String> completeNodes(String prefix) {
        return plugin.knownNodes().complete(prefix);
    }

    public List<String> filter(String prefix, String... options) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return Stream.of(options).filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    public List<String> filter(String prefix, List<String> options) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    public Node buildPermission(String raw, boolean value, long expiry, ImmutableContextSet contexts) {
        return NodeShorthand.parse(raw, value, expiry, contexts);
    }
}
