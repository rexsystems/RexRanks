package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

public final class GroupCommands {
    private final RexRanksPlugin plugin;
    private final CommandSupport s;
    private final SubjectOps ops;

    public GroupCommands(RexRanksPlugin plugin, CommandSupport support, SubjectOps ops) {
        this.plugin = plugin;
        this.s = support;
        this.ops = ops;
    }

    public void handle(CommandSender sender, Args args, String label) {
        if (!s.permit(sender, "group")) return;
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " group <name|list> <info|create|delete|…>");
            return;
        }
        String name = args.next();
        if (name.equalsIgnoreCase("list") || name.equalsIgnoreCase("ls")) {
            list(sender);
            return;
        }
        String action = args.hasNext() ? args.next().toLowerCase(Locale.ROOT) : "info";
        if (action.equals("create") || action.equals("new")) {
            if (plugin.storage().getGroup(name).isPresent()) {
                plugin.messages().send(sender, "group.exists", "group", name);
                return;
            }
            GroupImpl group = (GroupImpl) plugin.groupManager().createAndLoadGroup(name).join();
            if (args.hasNext()) {
                try {
                    group.setWeight(Integer.parseInt(args.peek()));
                    args.next();
                } catch (NumberFormatException ignored) {
                }
            }
            if (args.hasNext()) {
                group.setDisplayName(args.rest());
            }
            s.saveGroup(group);
            plugin.messages().send(sender, "group.created", "group", group.getName());
            return;
        }
        GroupImpl group = s.resolveGroup(sender, name, false).orElse(null);
        if (group == null) return;
        switch (action) {
            case "info", "i" -> info(sender, group);
            case "delete", "remove", "rm" -> {
                if (group.getName().equalsIgnoreCase(plugin.config().defaultGroup())) {
                    plugin.messages().send(sender, "group.cannot-delete-default");
                    return;
                }
                plugin.groupManager().deleteGroup(group).join();
                plugin.invalidateAllCaches();
                plugin.notifyChange("group:" + group.getName());
                plugin.messages().send(sender, "group.deleted", "group", group.getName());
            }
            case "permission", "perm", "p" -> ops.permission(sender, args, "group " + group.getName(), group.data(), () -> s.saveGroup(group), label);
            case "parent", "parents", "inherit" -> parent(sender, group, args, label);
            case "meta" -> ops.meta(sender, args, "group " + group.getName(), group.data(), () -> s.saveGroup(group), label);
            case "setweight", "weight" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " setweight <weight>");
                    return;
                }
                try {
                    group.setWeight(Integer.parseInt(args.next()));
                    s.saveGroup(group);
                    plugin.messages().send(sender, "group.weight", "group", group.getName(), "weight", String.valueOf(group.getWeight()));
                } catch (NumberFormatException e) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " setweight <weight>");
                }
            }
            case "setdisplayname", "displayname", "setdisplay" -> {
                String display = args.rest();
                group.setDisplayName(display.isBlank() ? null : display);
                s.saveGroup(group);
                plugin.messages().send(sender, "group.display", "group", group.getName(), "preview", group.getDisplayName());
            }
            case "rename" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " rename <new>");
                    return;
                }
                String neu = args.next().toLowerCase(Locale.ROOT);
                if (plugin.storage().getGroup(neu).isPresent()) {
                    plugin.messages().send(sender, "group.exists", "group", neu);
                    return;
                }
                GroupImpl created = (GroupImpl) plugin.groupManager().createAndLoadGroup(neu).join();
                created.dataImpl().replaceAll(group.data().toSet());
                s.saveGroup(created);
                plugin.groupManager().deleteGroup(group).join();
                plugin.messages().send(sender, "group.renamed", "from", group.getName(), "to", neu);
            }
            case "clone" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " clone <new>");
                    return;
                }
                String neu = args.next().toLowerCase(Locale.ROOT);
                GroupImpl created = (GroupImpl) plugin.groupManager().createAndLoadGroup(neu).join();
                created.dataImpl().replaceAll(group.data().toSet());
                s.saveGroup(created);
                plugin.messages().send(sender, "group.cloned", "source", group.getName(), "target", neu);
            }
            case "editor", "ed" -> plugin.editorService().openForGroup(sender, group);
            default -> s.usage(sender, "/" + label + " group " + group.getName() + " <info|create|delete|permission|parent|meta|setweight|setdisplayname|rename|clone|editor>");
        }
    }

    private void list(CommandSender sender) {
        var groups = plugin.storage().getLoadedGroups().stream()
                .sorted(Comparator.comparingInt(GroupImpl::getWeight).reversed().thenComparing(GroupImpl::getName))
                .toList();
        plugin.messages().send(sender, "group.list-header", "count", String.valueOf(groups.size()));
        for (GroupImpl group : groups) {
            String display = group.getDisplayName().equals(group.getName()) ? "" : " <gray>(" + group.getDisplayName() + ")";
            sender.sendMessage(Text.parse("  <gold>" + group.getName() + " <gray>w=" + group.getWeight()
                    + " <dark_gray>nodes=" + group.data().size() + display));
        }
    }

    private void info(CommandSender sender, GroupImpl group) {
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Group <white>" + group.getName()
                + "  <gray>weight <white>" + group.getWeight()));
        sender.sendMessage(Text.parse("  <gray>Display: ").append(Text.parse(group.getDisplayName())));
        Set<String> parents = group.getParents();
        sender.sendMessage(Text.parse("  <gray>Parents: <white>" + (parents.isEmpty() ? "(none)" : String.join(", ", parents))));
        s.sendNodeList(sender, "Nodes (" + group.data().size() + ")", group.data().toSet().stream()
                .sorted(Comparator.comparing(n -> n.type().name() + n.key())).toList());
    }

    private void parent(CommandSender sender, GroupImpl group, Args args, String label) {
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " group " + group.getName() + " parent <add|remove|clear|info> [group]");
            return;
        }
        String action = args.next().toLowerCase(Locale.ROOT);
        switch (action) {
            case "info", "list" -> s.sendNodeList(sender, group.getName() + " parents", group.data().ofType(NodeType.INHERITANCE));
            case "clear" -> {
                group.data().clear(NodeType.INHERITANCE);
                s.saveGroup(group);
                plugin.messages().send(sender, "saved");
            }
            case "add" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " parent add <group>");
                    return;
                }
                String parent = args.next().toLowerCase(Locale.ROOT);
                if (parent.equalsIgnoreCase(group.getName())) {
                    sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>A group cannot inherit itself."));
                    return;
                }
                if (s.resolveGroup(sender, parent, false).isEmpty()) return;
                group.data().addOrReplace(Node.inheritance(parent));
                s.saveGroup(group);
                plugin.messages().send(sender, "saved");
            }
            case "remove", "rem" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " group " + group.getName() + " parent remove <group>");
                    return;
                }
                group.data().removeIf(n -> n.type() == NodeType.INHERITANCE && n.groupName().equalsIgnoreCase(args.next()));
                s.saveGroup(group);
                plugin.messages().send(sender, "saved");
            }
            default -> s.usage(sender, "/" + label + " group " + group.getName() + " parent <add|remove|clear|info>");
        }
    }
}
