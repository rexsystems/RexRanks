package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.engine.NodeShorthand;
import me.rexsystems.rexranks.engine.WildcardMatcher;
import me.rexsystems.rexranks.importing.DataExporter;
import me.rexsystems.rexranks.importing.ImportResult;
import me.rexsystems.rexranks.importing.LuckPermsImporter;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MiscCommands {
    private final RexRanksPlugin plugin;
    private final CommandSupport s;

    public MiscCommands(RexRanksPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.s = support;
    }

    public void help(CommandSender sender, String label) {
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Commands"));
        String[][] rows = {
                {"/" + label + " user <user> …", "Inspect and edit a user"},
                {"/" + label + " group <group> …", "Inspect and edit a group"},
                {"/" + label + " track <track> …", "Manage promotion tracks"},
                {"/" + label + " editor [user|group] [name]", "Open the web editor"},
                {"/" + label + " apply <code>", "Apply a remote editor session"},
                {"/" + label + " color …", "Colour / gradient workshop"},
                {"/" + label + " check <user> <node>", "Test a permission"},
                {"/" + label + " verbose", "Toggle permission spy"},
                {"/" + label + " search <node>", "Find who has a node"},
                {"/" + label + " tree [group]", "Print inheritance"},
                {"/" + label + " import luckperms <file>", "Import a LuckPerms export"},
                {"/" + label + " export [file]", "Export all data as JSON"},
                {"/" + label + " bulkupdate …", "Bulk set or unset nodes"},
                {"/" + label + " sync [pull|push]", "Network synchronisation"},
                {"/" + label + " info", "Plugin and storage status"},
                {"/" + label + " reload", "Reload config and messages"},
                {"/" + label + " migrate <yaml|h2|sqlite>", "Copy data to another backend"},
        };
        for (String[] row : rows) {
            sender.sendMessage(Text.parse("  <gold>" + row[0] + " <dark_gray>— <gray>" + row[1]));
        }
    }

    public void editor(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "editor")) return;
        if (!args.hasNext()) {
            plugin.editorService().openFull(sender);
            return;
        }
        String kind = args.next().toLowerCase(Locale.ROOT);
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " editor <user|group> <name>");
            return;
        }
        String name = args.next();
        if (kind.equals("user") || kind.equals("u")) {
            s.withUser(sender, name, user -> plugin.editorService().openForUser(sender, user));
        } else if (kind.equals("group") || kind.equals("g")) {
            GroupImpl group = s.resolveGroup(sender, name, false).orElse(null);
            if (group != null) plugin.editorService().openForGroup(sender, group);
        } else {
            s.usage(sender, "/" + label + " editor [user|group] [name]");
        }
    }

    public void apply(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "apply")) return;
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " apply <code>");
            return;
        }
        plugin.editorService().applyCode(sender, args.next());
    }

    public void verbose(CommandSender sender) {
        if (!s.permit(sender, "verbose")) return;
        boolean on = plugin.verbose().toggle(sender);
        plugin.messages().send(sender, on ? "check.verbose-on" : "check.verbose-off");
    }

    public void check(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "check")) return;
        if (args.remaining() < 2) {
            s.usage(sender, "/" + label + " check <user> <permission>");
            return;
        }
        String name = args.next();
        String node = args.rest();
        s.withUser(sender, name, user -> {
            QueryOptions options = plugin.contextService().optionsFor(user.getUniqueId());
            Tristate result = user.checkPermission(node, options);
            String origin = plugin.calculator().resolve(user, options).stream()
                    .filter(r -> r.node().type().isPermission())
                    .filter(r -> WildcardMatcher.score(r.node().key(), node, r.node().type() == NodeType.REGEX_PERMISSION) >= 0)
                    .max((a, b) -> Integer.compare(
                            WildcardMatcher.score(a.node().key(), node, a.node().type() == NodeType.REGEX_PERMISSION),
                            WildcardMatcher.score(b.node().key(), node, b.node().type() == NodeType.REGEX_PERMISSION)))
                    .map(r -> r.origin() + " · " + NodeShorthand.toShorthand(r.node()))
                    .orElse("none");
            plugin.messages().send(sender, "check.result", Map.of(
                    "name", user.getUsername(), "node", node, "value", s.tristate(result), "origin", origin));
        });
    }

    public void info(CommandSender sender) {
        if (!s.permit(sender, "info")) return;
        plugin.messages().send(sender, "info.header", "version", plugin.getPluginMeta().getVersion());
        sender.sendMessage(Text.parse("  <gray>Storage: <white>" + plugin.storage().name()));
        sender.sendMessage(Text.parse("  <gray>Users: <white>" + plugin.storage().getLoadedUsers().size()
                + "  <gray>Groups: <white>" + plugin.storage().getLoadedGroups().size()
                + "  <gray>Tracks: <white>" + plugin.storage().getLoadedTracks().size()));
        sender.sendMessage(Text.parse("  <gray>Server context: <white>" + plugin.config().serverName()));
        sender.sendMessage(Text.parse("  <gray>Sync: <white>" + (plugin.config().syncEnabled() ? String.valueOf(plugin.config().syncMethods()) : "disabled")));
        sender.sendMessage(Text.parse("  <gray>Editor: <white>" + (plugin.config().editorEnabled() ? plugin.config().editorMode() + " :" + plugin.config().editorPort() : "disabled")));
        sender.sendMessage(Text.parse("  <gray>Folia: <white>" + plugin.schedulers().isFolia()));
    }

    public void sync(CommandSender sender, Args args) {
        if (!s.permit(sender, "sync")) return;
        if (plugin.syncService() == null || !plugin.config().syncEnabled()) {
            plugin.messages().send(sender, "sync.disabled");
            return;
        }
        String action = args.hasNext() ? args.next().toLowerCase(Locale.ROOT) : "push";
        if (action.equals("pull")) {
            int n = plugin.syncService().pull();
            plugin.messages().send(sender, "sync.pulled", "count", String.valueOf(n));
        } else {
            plugin.syncService().push("command");
            plugin.messages().send(sender, "sync.pushed");
        }
    }

    public void reload(CommandSender sender) {
        if (!s.permit(sender, "reload")) return;
        try {
            plugin.reloadAll();
            plugin.messages().send(sender, "reloaded");
        } catch (Exception e) {
            plugin.messages().send(sender, "reload-failed", "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public void importData(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "import")) return;
        if (args.remaining() < 2 || !args.next().equalsIgnoreCase("luckperms")) {
            s.usage(sender, "/" + label + " import luckperms <file>");
            return;
        }
        String file = args.rest();
        Path path = Path.of(file);
        if (!path.isAbsolute()) path = plugin.getDataFolder().toPath().resolve(file);
        plugin.messages().send(sender, "import.started", "file", path.toString());
        Path finalPath = path;
        plugin.schedulers().runAsync(() -> {
            try {
                ImportResult result = new LuckPermsImporter(plugin.storage(), plugin.storageContext()).importFile(finalPath);
                plugin.invalidateAllCaches();
                plugin.notifyChange("import");
                plugin.schedulers().runSync(() -> plugin.messages().send(sender, "import.done", "summary", result.summary()));
            } catch (Exception e) {
                plugin.schedulers().runSync(() -> plugin.messages().send(sender, "import.fail", "error", String.valueOf(e.getMessage())));
            }
        });
    }

    public void exportData(CommandSender sender, Args args) {
        if (!s.permit(sender, "export")) return;
        String file = args.hasNext() ? args.rest() : "exports/rexranks-" + System.currentTimeMillis() + ".json";
        Path path = Path.of(file);
        if (!path.isAbsolute()) path = plugin.getDataFolder().toPath().resolve(file);
        Path finalPath = path;
        plugin.schedulers().runAsync(() -> {
            try {
                new DataExporter().write(plugin.getPluginMeta().getVersion(), plugin.storage(), finalPath);
                plugin.schedulers().runSync(() -> plugin.messages().send(sender, "export.done", "file", finalPath.toString()));
            } catch (Exception e) {
                plugin.schedulers().runSync(() -> plugin.messages().send(sender, "export.fail", "error", String.valueOf(e.getMessage())));
            }
        });
    }

    public void search(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "search")) return;
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " search <node>");
            return;
        }
        String query = args.rest().toLowerCase(Locale.ROOT);
        List<Component> hits = new ArrayList<>();
        plugin.storage().getLoadedUsers().forEach(user -> user.data().toSet().forEach(node -> {
            if (matches(node, query)) {
                hits.add(Text.parse("  <gray>user <white>" + user.getUsername() + " <dark_gray>" + NodeShorthand.toShorthand(node)));
            }
        }));
        plugin.storage().getLoadedGroups().forEach(group -> group.data().toSet().forEach(node -> {
            if (matches(node, query)) {
                hits.add(Text.parse("  <gray>group <gold>" + group.getName() + " <dark_gray>" + NodeShorthand.toShorthand(node)));
            }
        }));
        if (hits.isEmpty()) {
            plugin.messages().send(sender, "search.none", "node", query);
            return;
        }
        plugin.messages().send(sender, "search.header", "node", query, "count", String.valueOf(hits.size()));
        hits.forEach(sender::sendMessage);
    }

    private boolean matches(Node node, String query) {
        String shorthand = NodeShorthand.toShorthand(node).toLowerCase(Locale.ROOT);
        return shorthand.contains(query) || node.key().toLowerCase(Locale.ROOT).contains(query)
                || (query.endsWith("*") && node.key().startsWith(query.substring(0, query.length() - 1)));
    }

    public void bulk(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "bulkupdate")) return;
        if (args.remaining() < 4) {
            s.usage(sender, "/" + label + " bulkupdate <users|groups> permission <set|unset> <node> [true|false] [context]");
            return;
        }
        String scope = args.next().toLowerCase(Locale.ROOT);
        if (!args.next().equalsIgnoreCase("permission")) {
            s.usage(sender, "/" + label + " bulkupdate <users|groups> permission <set|unset> <node> …");
            return;
        }
        String action = args.next().toLowerCase(Locale.ROOT);
        String nodeKey = args.next();
        boolean value = args.nextBoolean().orElse(true);
        ImmutableContextSet contexts = args.remainingContexts();
        int changed = 0;
        if (scope.equals("users") || scope.equals("user")) {
            for (UserImpl user : plugin.storage().getLoadedUsers()) {
                if (applyBulk(user.data(), action, nodeKey, value, contexts)) {
                    s.saveUser(user);
                    changed++;
                }
            }
        } else if (scope.equals("groups") || scope.equals("group")) {
            for (GroupImpl group : plugin.storage().getLoadedGroups()) {
                if (applyBulk(group.data(), action, nodeKey, value, contexts)) {
                    s.saveGroup(group);
                    changed++;
                }
            }
        } else {
            s.usage(sender, "/" + label + " bulkupdate <users|groups> permission <set|unset> <node> …");
            return;
        }
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <green>Bulk update applied to <white>" + changed + " <green>subjects."));
    }

    private boolean applyBulk(me.rexsystems.rexranks.api.data.NodeMap data, String action, String nodeKey, boolean value, ImmutableContextSet contexts) {
        if (action.equals("set")) {
            data.addOrReplace(s.buildPermission(nodeKey, value, 0, contexts));
            return true;
        }
        if (action.equals("unset")) {
            Node parsed = NodeShorthand.parse(nodeKey);
            int n = data.removeIf(existing -> existing.type() == parsed.type()
                    && existing.key().equalsIgnoreCase(parsed.key())
                    && (contexts.isEmpty() || existing.contexts().equals(contexts)));
            return n > 0;
        }
        return false;
    }

    public void tree(CommandSender sender, Args args) {
        if (!s.permit(sender, "group")) return;
        if (args.hasNext()) {
            GroupImpl group = s.resolveGroup(sender, args.next(), false).orElse(null);
            if (group == null) return;
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Inheritance tree of <white>" + group.getName()));
            printTree(sender, group.getName(), 0, new HashSet<>());
            return;
        }
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>All groups"));
        plugin.storage().getLoadedGroups().stream()
                .sorted(Comparator.comparingInt(GroupImpl::getWeight).reversed())
                .forEach(g -> sender.sendMessage(Text.parse("  <gold>" + g.getName() + " <gray>w=" + g.getWeight()
                        + (g.getParents().isEmpty() ? "" : " <dark_gray>→ " + String.join(", ", g.getParents())))));
    }

    private void printTree(CommandSender sender, String name, int depth, Set<String> visiting) {
        if (!visiting.add(name.toLowerCase(Locale.ROOT))) {
            sender.sendMessage(Text.parse("  " + "  ".repeat(depth) + "<red>" + name + " <dark_gray>(cycle)"));
            return;
        }
        GroupImpl group = plugin.storage().getGroup(name).orElse(null);
        String label = group == null ? name + " <red>(missing)" : name + " <gray>w=" + group.getWeight();
        sender.sendMessage(Text.parse("  " + "  ".repeat(depth) + "<gold>" + label));
        if (group != null) {
            for (String parent : group.getParents()) printTree(sender, parent, depth + 1, visiting);
        }
        visiting.remove(name.toLowerCase(Locale.ROOT));
    }

    public void migrate(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "admin")) return;
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " migrate <yaml|h2|sqlite>");
            return;
        }
        plugin.migrateStorage(sender, args.next().toLowerCase(Locale.ROOT));
    }
}
