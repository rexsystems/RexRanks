package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.util.Args;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RexRanksCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of(
            "user", "group", "track", "editor", "apply", "color", "verbose",
            "check", "info", "sync", "reload", "import", "export", "search",
            "bulkupdate", "tree", "help", "migrate"
    );

    private final RexRanksPlugin plugin;
    private final CommandSupport support;
    private final UserCommands users;
    private final GroupCommands groups;
    private final TrackCommands tracks;
    private final MiscCommands misc;

    public RexRanksCommand(RexRanksPlugin plugin) {
        this.plugin = plugin;
        this.support = new CommandSupport(plugin);
        SubjectOps ops = new SubjectOps(plugin, support);
        this.users = new UserCommands(plugin, support, ops);
        this.groups = new GroupCommands(plugin, support, ops);
        this.tracks = new TrackCommands(plugin, support);
        this.misc = new MiscCommands(plugin, support);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] raw) {
        Args args = new Args(raw);
        if (!args.hasNext()) {
            misc.help(sender, label);
            return true;
        }
        String sub = args.next().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "user", "u", "player" -> users.handle(sender, args, label);
            case "group", "g", "groups" -> groups.handle(sender, args, label);
            case "track", "t", "tracks" -> tracks.handle(sender, args, label);
            case "editor", "ed", "web" -> misc.editor(sender, args, label);
            case "apply" -> misc.apply(sender, args, label);
            case "color", "colour", "rgb" -> plugin.colorWorkshop().handle(sender, args, label);
            case "verbose", "v" -> misc.verbose(sender);
            case "check" -> misc.check(sender, args, label);
            case "info", "about" -> misc.info(sender);
            case "sync" -> misc.sync(sender, args);
            case "reload" -> misc.reload(sender);
            case "import" -> misc.importData(sender, args, label);
            case "export" -> misc.exportData(sender, args);
            case "search" -> misc.search(sender, args, label);
            case "bulkupdate", "bulk" -> misc.bulk(sender, args, label);
            case "tree" -> misc.tree(sender, args);
            case "help", "?" -> misc.help(sender, label);
            case "migrate" -> misc.migrate(sender, args, label);
            default -> support.usage(sender, "/" + label + " <user|group|track|editor|color|...>  (try /" + label + " help)");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] raw) {
        if (!support.permitSilent(sender, "use")) {
            return List.of();
        }
        if (raw.length <= 1) {
            return support.filter(raw.length == 0 ? "" : raw[0], ROOT);
        }
        String sub = raw[0].toLowerCase(Locale.ROOT);
        String last = raw[raw.length - 1];
        return switch (sub) {
            case "user", "u", "player" -> completeUser(raw, last);
            case "group", "g", "groups" -> completeGroup(raw, last);
            case "track", "t", "tracks" -> completeTrack(raw, last);
            case "editor", "ed", "web" -> raw.length == 2 ? support.filter(last, "user", "group")
                    : raw.length == 3 && raw[1].toLowerCase(Locale.ROOT).startsWith("u") ? support.completePlayers(last)
                    : raw.length == 3 ? support.completeGroups(last) : List.of();
            case "check" -> raw.length == 2 ? support.completePlayers(last) : support.completeNodes(last);
            case "import" -> raw.length == 2 ? support.filter(last, "luckperms") : List.of();
            case "sync" -> raw.length == 2 ? support.filter(last, "push", "pull") : List.of();
            case "bulkupdate", "bulk" -> completeBulk(raw, last);
            case "tree" -> raw.length == 2 ? support.completeGroups(last) : List.of();
            case "migrate" -> raw.length == 2 ? support.filter(last, "yaml", "h2", "sqlite") : List.of();
            case "color", "colour", "rgb" -> plugin.colorWorkshop().complete(raw, last);
            default -> List.of();
        };
    }

    private List<String> completeUser(String[] raw, String last) {
        if (raw.length == 2) return support.completePlayers(last);
        if (raw.length == 3) {
            return support.filter(last, "info", "permission", "parent", "meta", "promote", "demote",
                    "editor", "clear", "clone", "showtracks", "switchprimary");
        }
        return switch (raw[2].toLowerCase(Locale.ROOT)) {
            case "permission", "perm", "p" -> completePerm(raw, last, 3);
            case "parent", "parents" -> completeParent(raw, last, 3);
            case "meta" -> completeMeta(raw, last, 3);
            case "promote", "demote" -> raw.length == 4 ? support.completeTracks(last) : List.of();
            case "clone" -> raw.length == 4 ? support.completePlayers(last) : List.of();
            case "switchprimary", "primary", "setprimary" -> raw.length == 4 ? support.completeGroups(last) : List.of();
            default -> List.of();
        };
    }

    private List<String> completeGroup(String[] raw, String last) {
        if (raw.length == 2) {
            List<String> names = new ArrayList<>(support.completeGroups(last));
            names.addAll(support.filter(last, "list"));
            return names;
        }
        if (raw.length == 3) {
            return support.filter(last, "info", "create", "delete", "permission", "parent", "meta",
                    "setweight", "setdisplayname", "rename", "clone", "editor");
        }
        return switch (raw[2].toLowerCase(Locale.ROOT)) {
            case "permission", "perm", "p" -> completePerm(raw, last, 3);
            case "parent", "parents", "inherit" -> completeParent(raw, last, 3);
            case "meta" -> completeMeta(raw, last, 3);
            case "clone", "rename" -> raw.length == 4 ? support.completeGroups(last) : List.of();
            default -> List.of();
        };
    }

    private List<String> completeTrack(String[] raw, String last) {
        if (raw.length == 2) {
            List<String> names = new ArrayList<>(support.completeTracks(last));
            names.addAll(support.filter(last, "list"));
            return names;
        }
        if (raw.length == 3) {
            return support.filter(last, "info", "create", "delete", "append", "insert", "remove", "clear", "rename");
        }
        String action = raw[2].toLowerCase(Locale.ROOT);
        if (action.equals("append") || action.equals("add") || action.equals("remove") || (action.equals("insert") && raw.length == 5)) {
            return support.completeGroups(last);
        }
        return List.of();
    }

    private List<String> completePerm(String[] raw, String last, int actionIndex) {
        if (raw.length == actionIndex + 1) return support.filter(last, "set", "unset", "tempset", "check", "info", "clear");
        if (raw.length == actionIndex + 2) return support.completeNodes(last);
        if (raw.length == actionIndex + 3) return support.filter(last, "true", "false");
        return support.filter(last, "true", "false", "1d", "1h", "30m", "7d", "world=", "server=");
    }

    private List<String> completeParent(String[] raw, String last, int actionIndex) {
        if (raw.length == actionIndex + 1) return support.filter(last, "add", "addtemp", "remove", "set", "clear", "info");
        return support.completeGroups(last);
    }

    private List<String> completeMeta(String[] raw, String last, int actionIndex) {
        if (raw.length == actionIndex + 1) {
            return support.filter(last, "set", "unset", "info", "setprefix", "setsuffix", "removeprefix", "removesuffix");
        }
        return List.of();
    }

    private List<String> completeBulk(String[] raw, String last) {
        if (raw.length == 2) return support.filter(last, "users", "groups");
        if (raw.length == 3) return support.filter(last, "permission");
        if (raw.length == 4) return support.filter(last, "set", "unset");
        if (raw.length == 5) return support.completeNodes(last);
        if (raw.length == 6) return support.filter(last, "true", "false");
        return List.of();
    }
}
