package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

public final class TrackCommands {
    private final RexRanksPlugin plugin;
    private final CommandSupport s;

    public TrackCommands(RexRanksPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.s = support;
    }

    public void handle(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "track")) return;
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " track <name|list> <info|create|delete|append|insert|remove|clear|rename>");
            return;
        }
        String name = args.next();
        if (name.equalsIgnoreCase("list") || name.equalsIgnoreCase("ls")) {
            list(sender);
            return;
        }
        String action = args.hasNext() ? args.next().toLowerCase(Locale.ROOT) : "info";
        if (action.equals("create") || action.equals("new")) {
            TrackImpl track = (TrackImpl) plugin.trackManager().createAndLoadTrack(name).join();
            plugin.messages().send(sender, "track.created", "track", track.getName());
            plugin.notifyChange("track:" + track.getName());
            return;
        }
        TrackImpl track = plugin.storage().getTrack(name).orElse(null);
        if (track == null) {
            plugin.messages().send(sender, "track-not-found", "track", name);
            return;
        }
        switch (action) {
            case "info", "i" -> {
                sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Track <white>" + track.getName()));
                sender.sendMessage(Text.parse("  <gray>" + CommandUtil.trackPath(track.getGroups(), -1)));
            }
            case "delete", "rm" -> {
                plugin.trackManager().deleteTrack(track).join();
                plugin.notifyChange("track:" + track.getName());
                plugin.messages().send(sender, "track.deleted", "track", track.getName());
            }
            case "append", "add" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " track " + track.getName() + " append <group>");
                    return;
                }
                String group = args.next();
                if (s.resolveGroup(sender, group, false).isEmpty()) return;
                track.appendGroup(group);
                plugin.trackManager().saveTrack(track);
                plugin.notifyChange("track:" + track.getName());
                plugin.messages().send(sender, "track.appended", "group", group, "track", track.getName());
            }
            case "insert" -> {
                if (args.remaining() < 2) {
                    s.usage(sender, "/" + label + " track " + track.getName() + " insert <index> <group>");
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(args.next());
                } catch (NumberFormatException e) {
                    s.usage(sender, "/" + label + " track " + track.getName() + " insert <index> <group>");
                    return;
                }
                String group = args.next();
                if (s.resolveGroup(sender, group, false).isEmpty()) return;
                track.insertGroup(index, group);
                plugin.trackManager().saveTrack(track);
                plugin.notifyChange("track:" + track.getName());
                plugin.messages().send(sender, "track.inserted", Map.of("group", group, "track", track.getName(), "index", String.valueOf(index)));
            }
            case "remove", "rem" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " track " + track.getName() + " remove <group>");
                    return;
                }
                String group = args.next();
                track.removeGroup(group);
                plugin.trackManager().saveTrack(track);
                plugin.notifyChange("track:" + track.getName());
                plugin.messages().send(sender, "track.removed", "group", group, "track", track.getName());
            }
            case "clear" -> {
                track.clearGroups();
                plugin.trackManager().saveTrack(track);
                plugin.notifyChange("track:" + track.getName());
                plugin.messages().send(sender, "track.cleared", "track", track.getName());
            }
            case "rename" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " track " + track.getName() + " rename <new>");
                    return;
                }
                String neu = args.next();
                TrackImpl created = (TrackImpl) plugin.trackManager().createAndLoadTrack(neu).join();
                created.setGroups(track.getGroups());
                plugin.trackManager().saveTrack(created);
                plugin.trackManager().deleteTrack(track).join();
                plugin.messages().send(sender, "track.renamed", "from", track.getName(), "to", created.getName());
            }
            default -> s.usage(sender, "/" + label + " track " + track.getName() + " <info|create|delete|append|insert|remove|clear|rename>");
        }
    }

    private void list(CommandSender sender) {
        var tracks = plugin.storage().getLoadedTracks();
        plugin.messages().send(sender, "track.list-header", "count", String.valueOf(tracks.size()));
        for (TrackImpl track : tracks) {
            sender.sendMessage(Text.parse("  <gold>" + track.getName() + " <gray>" + CommandUtil.trackPath(track.getGroups(), -1)));
        }
    }
}
