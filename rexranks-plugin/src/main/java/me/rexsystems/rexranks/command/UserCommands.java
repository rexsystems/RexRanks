package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class UserCommands {
    private final RexRanksPlugin plugin;
    private final CommandSupport s;
    private final SubjectOps ops;

    public UserCommands(RexRanksPlugin plugin, CommandSupport support, SubjectOps ops) {
        this.plugin = plugin;
        this.s = support;
        this.ops = ops;
    }

    public void handle(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "user")) {
            return;
        }
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " user <user> <info|permission|parent|meta|promote|demote|editor|clear|clone|showtracks|switchprimary>");
            return;
        }
        String name = args.next();
        String action = args.hasNext() ? args.next().toLowerCase(Locale.ROOT) : "info";
        s.withUser(sender, name, user -> dispatch(sender, args, label, user, action));
    }

    private void dispatch(CommandSender sender, Args args, String label, UserImpl user, String action) {
        switch (action) {
            case "info", "i" -> info(sender, user);
            case "permission", "perm", "p" -> ops.permission(sender, args, "user " + user.getUsername(), user.data(), () -> s.saveUser(user), label);
            case "parent", "parents", "group", "groups" -> parent(sender, user, args, label);
            case "meta" -> ops.meta(sender, args, "user " + user.getUsername(), user.data(), () -> s.saveUser(user), label);
            case "promote" -> promote(sender, user, args, true);
            case "demote" -> promote(sender, user, args, false);
            case "editor", "ed" -> plugin.editorService().openForUser(sender, user);
            case "clear" -> {
                user.data().clear();
                s.saveUser(user);
                plugin.messages().send(sender, "user.cleared", "target", user.getUsername());
            }
            case "clone" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " user " + user.getUsername() + " clone <other>");
                    return;
                }
                s.withUser(sender, args.next(), other -> {
                    other.dataImpl().replaceAll(user.data().toSet());
                    other.setPrimaryGroup(user.getPrimaryGroup());
                    s.saveUser(other);
                    plugin.messages().send(sender, "user.cloned", "source", user.getUsername(), "target", other.getUsername());
                });
            }
            case "showtracks", "tracks" -> showTracks(sender, user);
            case "switchprimary", "primary", "setprimary" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " user " + user.getUsername() + " switchprimary <group>");
                    return;
                }
                String group = args.next().toLowerCase(Locale.ROOT);
                if (plugin.storage().getGroup(group).isEmpty()) {
                    plugin.messages().send(sender, "group-not-found", "group", group);
                    return;
                }
                user.setPrimaryGroup(group);
                s.saveUser(user);
                plugin.messages().send(sender, "user.primary", "target", user.getUsername(), "group", group);
            }
            default -> s.usage(sender, "/" + label + " user " + user.getUsername() + " <info|permission|parent|meta|promote|demote|editor|clear|clone|showtracks|switchprimary>");
        }
    }

    private void info(CommandSender sender, UserImpl user) {
        QueryOptions options = plugin.contextService().optionsFor(user.getUniqueId());
        CachedMetaData meta = user.getCachedData().getMetaData(options);
        plugin.messages().send(sender, "user.info-header", Map.of("name", user.getUsername(), "uuid", user.getUniqueId().toString()));
        sender.sendMessage(Text.parse("  <gray>Primary: <white>" + user.getPrimaryGroup() + "  <gray>Weight: <white>" + meta.getWeight()));
        String prefix = meta.getPrefix().isEmpty() ? "<dark_gray>(none)" : meta.getPrefix();
        String suffix = meta.getSuffix().isEmpty() ? "<dark_gray>(none)" : meta.getSuffix();
        sender.sendMessage(Text.parse("  <gray>Prefix: ").append(Text.parse(prefix)));
        sender.sendMessage(Text.parse("  <gray>Suffix: ").append(Text.parse(suffix)));
        Set<String> parents = user.getCachedData().getPermissionData(options).getGroups();
        sender.sendMessage(Text.parse("  <gray>Parents: <white>" + (parents.isEmpty() ? "(none)" : String.join(", ", parents))));
        s.sendNodeList(sender, "Nodes (" + user.data().size() + ")", user.data().toSet().stream()
                .sorted(Comparator.comparing(n -> n.type().name() + n.key())).toList());
    }

    private void parent(CommandSender sender, UserImpl user, Args args, String label) {
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " user " + user.getUsername() + " parent <add|remove|set|clear|info> [group] [duration] [context]");
            return;
        }
        String action = args.next().toLowerCase(Locale.ROOT);
        switch (action) {
            case "info", "list" -> s.sendNodeList(sender, user.getUsername() + " parents", user.data().ofType(NodeType.INHERITANCE));
            case "clear" -> {
                user.data().clear(NodeType.INHERITANCE);
                s.saveUser(user);
                plugin.messages().send(sender, "user.parent-remove", "target", user.getUsername(), "group", "(all)");
            }
            case "set" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " user " + user.getUsername() + " parent set <group>");
                    return;
                }
                String group = args.next().toLowerCase(Locale.ROOT);
                if (s.resolveGroup(sender, group, false).isEmpty()) return;
                user.data().clear(NodeType.INHERITANCE);
                user.data().add(Node.inheritance(group));
                user.setPrimaryGroup(group);
                s.saveUser(user);
                plugin.messages().send(sender, "user.parent-add", "target", user.getUsername(), "group", group, "extra", "");
            }
            case "add", "addtemp" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " user " + user.getUsername() + " parent add <group> [duration] [context]");
                    return;
                }
                String group = args.next().toLowerCase(Locale.ROOT);
                if (s.resolveGroup(sender, group, false).isEmpty()) return;
                var expiry = args.nextExpiry(ZoneId.systemDefault());
                ImmutableContextSet contexts = args.remainingContexts();
                Node node = Node.builder(NodeType.INHERITANCE, group)
                        .expiry(expiry.map(i -> i.toEpochMilli()).orElse(0L)).contexts(contexts).build();
                user.data().addOrReplace(node);
                s.saveUser(user);
                plugin.messages().send(sender, "user.parent-add", "target", user.getUsername(), "group", group, "extra", s.extra(node));
            }
            case "remove", "rem", "delete" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " user " + user.getUsername() + " parent remove <group>");
                    return;
                }
                String group = args.next().toLowerCase(Locale.ROOT);
                ImmutableContextSet contexts = args.remainingContexts();
                int n = user.data().removeIf(node -> node.type() == NodeType.INHERITANCE
                        && node.groupName().equalsIgnoreCase(group)
                        && (contexts.isEmpty() || node.contexts().equals(contexts)));
                if (n == 0) {
                    plugin.messages().send(sender, "user.already-has", "target", user.getUsername());
                    return;
                }
                s.saveUser(user);
                plugin.messages().send(sender, "user.parent-remove", "target", user.getUsername(), "group", group);
            }
            default -> s.usage(sender, "/" + label + " user " + user.getUsername() + " parent <add|remove|set|clear|info>");
        }
    }

    private void promote(CommandSender sender, UserImpl user, Args args, boolean up) {
        Track track;
        if (args.hasNext()) {
            track = plugin.trackManager().getTrack(args.next()).orElse(null);
            if (track == null) {
                plugin.messages().send(sender, "track-not-found", "track", "?");
                return;
            }
        } else {
            List<Track> matching = plugin.storage().getLoadedTracks().stream()
                    .filter(t -> t.getGroups().stream().anyMatch(g ->
                            g.equalsIgnoreCase(user.getPrimaryGroup())
                                    || user.data().ofType(NodeType.INHERITANCE).stream().anyMatch(n -> n.groupName().equalsIgnoreCase(g))))
                    .map(t -> (Track) t)
                    .toList();
            if (matching.size() == 1) {
                track = matching.getFirst();
            } else if (matching.isEmpty()) {
                plugin.messages().send(sender, "user.promote-fail", "error", user.getUsername() + " is not on any track. Specify one.");
                return;
            } else {
                plugin.messages().send(sender, "user.promote-fail", "error", "Matches several tracks (" +
                        matching.stream().map(Track::getName).collect(Collectors.joining(", ")) + "). Specify one.");
                return;
            }
        }
        Track.PromotionResult result = up ? track.promote(user) : track.demote(user);
        if (!result.success()) {
            plugin.messages().send(sender, "user.promote-fail", "error", result.message());
            return;
        }
        s.saveUser(user);
        plugin.messages().send(sender, up ? "user.promote" : "user.demote", Map.of(
                "name", user.getUsername(),
                "from", result.from() == null ? "(none)" : result.from(),
                "to", result.to(),
                "track", track.getName()
        ));
    }

    private void showTracks(CommandSender sender, UserImpl user) {
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Tracks for " + user.getUsername()));
        boolean any = false;
        for (var track : plugin.storage().getLoadedTracks()) {
            int idx = -1;
            for (int i = 0; i < track.getGroups().size(); i++) {
                String g = track.getGroups().get(i);
                if (g.equalsIgnoreCase(user.getPrimaryGroup())
                        || user.data().ofType(NodeType.INHERITANCE).stream().anyMatch(n -> n.groupName().equalsIgnoreCase(g))) {
                    idx = i;
                }
            }
            if (idx >= 0) {
                any = true;
                sender.sendMessage(Text.parse("  <gold>" + track.getName() + " <gray>" + CommandUtil.trackPath(track.getGroups(), idx)));
            }
        }
        if (!any) {
            sender.sendMessage(Text.parse("  <dark_gray>Not on any track."));
        }
    }
}
