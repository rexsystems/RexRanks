package me.rexsystems.rexranks.command;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.data.NodeMap;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.engine.NodeShorthand;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.util.Map;

public final class SubjectOps {
    private final RexRanksPlugin plugin;
    private final CommandSupport s;

    public SubjectOps(RexRanksPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.s = support;
    }

    public void permission(CommandSender sender, Args args, String target, NodeMap data, Runnable save, String label) {
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " " + target + " permission <set|unset|check|info|clear|tempset> …");
            return;
        }
        String action = args.next().toLowerCase();
        switch (action) {
            case "info", "list" -> s.sendNodeList(sender, target + " permissions",
                    data.toSet().stream().filter(n -> n.type().isPermission()).toList());
            case "clear" -> {
                data.toSet().stream().filter(n -> n.type().isPermission()).toList().forEach(data::remove);
                save.run();
                plugin.messages().send(sender, "saved");
            }
            case "set", "tempset" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " " + target + " permission set <node> [true|false] [duration] [context]");
                    return;
                }
                String nodeKey = args.next();
                boolean value = args.nextBoolean().orElse(true);
                long expiry = args.nextExpiry(ZoneId.systemDefault()).map(i -> i.toEpochMilli()).orElse(0L);
                ImmutableContextSet contexts = args.remainingContexts();
                Node node = s.buildPermission(nodeKey, value, expiry, contexts);
                data.addOrReplace(node);
                save.run();
                plugin.messages().send(sender, "user.permission-set", Map.of(
                        "node", NodeShorthand.toShorthand(node),
                        "value", value ? "true" : "false",
                        "target", target,
                        "extra", s.extra(node)
                ));
            }
            case "unset", "remove", "rem" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " " + target + " permission unset <node> [context]");
                    return;
                }
                String nodeKey = args.next();
                Node parsed = NodeShorthand.parse(nodeKey);
                ImmutableContextSet contexts = args.remainingContexts();
                int n = data.removeIf(existing -> existing.type() == parsed.type()
                        && existing.key().equalsIgnoreCase(parsed.key())
                        && (contexts.isEmpty() || existing.contexts().equals(contexts)));
                if (n == 0) {
                    plugin.messages().send(sender, "user.already-has", "target", target);
                    return;
                }
                save.run();
                plugin.messages().send(sender, "user.permission-unset", "node", nodeKey, "target", target);
            }
            case "check" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " " + target + " permission check <node>");
                    return;
                }
                String nodeKey = args.next();
                Tristate result = plugin.calculator().check(data.toSet(), null, nodeKey, QueryOptions.DEFAULT);
                plugin.messages().send(sender, "check.result", Map.of(
                        "name", target,
                        "node", nodeKey,
                        "value", s.tristate(result),
                        "origin", "direct"
                ));
            }
            default -> s.usage(sender, "/" + label + " " + target + " permission <set|unset|check|info|clear>");
        }
    }

    public void meta(CommandSender sender, Args args, String target, NodeMap data, Runnable save, String label) {
        if (!args.hasNext()) {
            s.usage(sender, "/" + label + " " + target + " meta <set|unset|info|setprefix|setsuffix|removeprefix|removesuffix>");
            return;
        }
        String action = args.next().toLowerCase();
        switch (action) {
            case "info", "list" -> s.sendNodeList(sender, target + " meta", data.toSet().stream()
                    .filter(n -> n.type() == NodeType.META || n.type().isChatMeta())
                    .toList());
            case "set" -> {
                if (args.remaining() < 2) {
                    s.usage(sender, "/" + label + " " + target + " meta set <key> <value> [duration] [context]");
                    return;
                }
                String key = args.next();
                String value = args.next();
                long expiry = args.nextExpiry(ZoneId.systemDefault()).map(i -> i.toEpochMilli()).orElse(0L);
                ImmutableContextSet contexts = args.remainingContexts();
                data.addOrReplace(Node.builder(NodeType.META, key).value(value).expiry(expiry).contexts(contexts).build());
                save.run();
                plugin.messages().send(sender, "user.meta-set", "key", key, "value", value, "target", target);
            }
            case "unset", "remove" -> {
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " " + target + " meta unset <key>");
                    return;
                }
                String key = args.next();
                data.removeMatching(NodeType.META, key);
                save.run();
                plugin.messages().send(sender, "user.meta-unset", "key", key, "target", target);
            }
            case "setprefix", "setsuffix" -> {
                boolean prefix = action.equals("setprefix");
                if (!args.hasNext()) {
                    s.usage(sender, "/" + label + " " + target + " meta " + action + " <priority> <text>");
                    return;
                }
                int priority = 100;
                try {
                    priority = Integer.parseInt(args.peek());
                    args.next();
                } catch (NumberFormatException ignored) {
                }
                String text = args.rest();
                if (text.isBlank()) {
                    s.usage(sender, "/" + label + " " + target + " meta " + action + " <priority> <text>");
                    return;
                }
                data.addOrReplace(prefix ? Node.prefix(priority, text) : Node.suffix(priority, text));
                save.run();
                plugin.messages().send(sender, prefix ? "user.prefix-set" : "user.suffix-set", "target", target, "preview", text);
                sender.sendMessage(Text.parse("  <gray>Preview: ").append(Text.parse(text)).append(Text.parse("<white>Player")));
            }
            case "removeprefix", "removesuffix" -> {
                NodeType type = action.equals("removeprefix") ? NodeType.PREFIX : NodeType.SUFFIX;
                if (args.hasNext()) {
                    data.removeMatching(type, args.next());
                } else {
                    data.clear(type);
                }
                save.run();
                plugin.messages().send(sender, "saved");
            }
            default -> s.usage(sender, "/" + label + " " + target + " meta <set|unset|info|setprefix|setsuffix|removeprefix|removesuffix>");
        }
    }
}
