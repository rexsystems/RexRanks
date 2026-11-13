package me.rexsystems.rexranks.color;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.engine.ColorFormatter;
import me.rexsystems.rexranks.engine.ColorFormatter.GradientStop;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.util.Args;
import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-game colour workshop: solid, multi-stop gradient, and rainbow builders
 * with live MiniMessage / legacy hex preview, clipboard copy, optional
 * apply-to-prefix/suffix, and a dyed-leather inventory picker.
 */
public final class ColorWorkshop implements Listener {
    private static final String GUI_TITLE = "RexRanks Colour Studio";
    private static final int[] SPECTRUM = {
            0xFF0000, 0xFF7F00, 0xFFFF00, 0x7FFF00, 0x00FF00, 0x00FF7F,
            0x00FFFF, 0x007FFF, 0x0000FF, 0x7F00FF, 0xFF00FF, 0xFF007F,
            0xFFFFFF, 0xC0C0C0, 0x808080, 0x000000, 0x8B4513, 0xFFD700
    };

    private final RexRanksPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> openGuis = new ConcurrentHashMap<>();

    public ColorWorkshop(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, Args args, String label) {
        if (!(sender.hasPermission("rexranks.color") || sender.hasPermission("rexranks.admin") || sender.isOp())) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!args.hasNext()) {
            help(sender, label);
            preview(sender, current(sender), "RexRanks");
            return;
        }
        String action = args.next().toLowerCase(Locale.ROOT);
        switch (action) {
            case "solid" -> solid(sender, args);
            case "gradient" -> gradient(sender, args);
            case "rainbow" -> rainbow(sender, args);
            case "hsv" -> hsv(sender, args);
            case "apply" -> apply(sender, args, label);
            case "gui", "menu" -> {
                if (sender instanceof Player player) {
                    openGui(player);
                } else {
                    plugin.messages().send(sender, "player-only");
                }
            }
            case "help" -> help(sender, label);
            default -> {
                // Treat leftover as sample text against the current session, or a hex solid.
                if (action.startsWith("#") || action.startsWith("&#")) {
                    Args rewritten = new Args(join(action, args.rest()));
                    solid(sender, rewritten);
                } else {
                    Session session = current(sender);
                    session.text = action + (args.hasNext() ? " " + args.rest() : "");
                    preview(sender, session, session.text);
                }
            }
        }
    }

    public List<String> complete(String[] raw, String last) {
        String prefix = last == null ? "" : last.toLowerCase(Locale.ROOT);
        if (raw.length <= 2) {
            return filter(prefix, "solid", "gradient", "rainbow", "hsv", "apply", "gui", "help");
        }
        String action = raw[1].toLowerCase(Locale.ROOT);
        if (action.equals("apply")) {
            if (raw.length == 3) {
                return filter(prefix, "prefix", "suffix");
            }
            if (raw.length == 4) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(p.getName());
                    }
                });
                plugin.storage().getLoadedGroups().forEach(g -> {
                    String token = "g:" + g.getName();
                    if (token.startsWith(prefix)) {
                        names.add(token);
                    }
                });
                return names;
            }
        }
        if (action.equals("solid") || action.equals("gradient")) {
            return filter(prefix, "#ff6b6b", "#ffd93d", "#6bcb77", "#4d96ff", "#c77dff", "#ffffff");
        }
        if (action.equals("rainbow")) {
            return filter(prefix, "true", "false");
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <gold>Colour workshop"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color solid <#hex> [text]"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color gradient <#hex> <#hex> … [text]"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color rainbow [!] [text]"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color hsv <h> <s> <v> [text]"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color apply <prefix|suffix> <user|g:group> [priority]"));
        sender.sendMessage(Text.parse("  <gold>/" + label + " color gui"));
    }

    private void solid(CommandSender sender, Args args) {
        if (!args.hasNext()) {
            plugin.messages().send(sender, "color.invalid-hex", "value", "");
            return;
        }
        String hex;
        try {
            hex = ColorFormatter.normalizeHex(args.next());
        } catch (IllegalArgumentException e) {
            plugin.messages().send(sender, "color.invalid-hex", "value", e.getMessage());
            return;
        }
        Session session = current(sender);
        session.mode = Mode.SOLID;
        session.stops = List.of(new Stop(0f, hex));
        if (args.hasNext()) {
            session.text = args.rest();
        }
        preview(sender, session, session.text);
    }

    private void gradient(CommandSender sender, Args args) {
        List<String> hexes = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean inText = false;
        while (args.hasNext()) {
            String token = args.next();
            if (!inText && looksLikeHex(token)) {
                try {
                    hexes.add(ColorFormatter.normalizeHex(token));
                } catch (IllegalArgumentException e) {
                    inText = true;
                    text.append(token);
                }
            } else {
                inText = true;
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(token);
            }
        }
        if (hexes.size() < 2) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>A gradient needs at least two hex colours."));
            return;
        }
        Session session = current(sender);
        session.mode = Mode.GRADIENT;
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < hexes.size(); i++) {
            float pos = hexes.size() == 1 ? 0f : (float) i / (float) (hexes.size() - 1);
            stops.add(new Stop(pos, hexes.get(i)));
        }
        session.stops = stops;
        if (!text.isEmpty()) {
            session.text = text.toString();
        }
        preview(sender, session, session.text);
    }

    private void rainbow(CommandSender sender, Args args) {
        Session session = current(sender);
        session.mode = Mode.RAINBOW;
        session.backwards = false;
        if (args.hasNext()) {
            String first = args.peek();
            if (first.equals("!") || first.equalsIgnoreCase("true") || first.equals("back") || first.equals("backwards")) {
                session.backwards = true;
                args.next();
            } else if (first.equalsIgnoreCase("false")) {
                args.next();
            }
        }
        if (args.hasNext()) {
            session.text = args.rest();
        }
        preview(sender, session, session.text);
    }

    private void hsv(CommandSender sender, Args args) {
        if (args.remaining() < 3) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>Usage: /rr color hsv <hue 0-360> <sat 0-100> <val 0-100> [text]"));
            return;
        }
        try {
            float h = Float.parseFloat(args.next());
            float s = Float.parseFloat(args.next());
            float v = Float.parseFloat(args.next());
            java.awt.Color awt = ColorFormatter.fromHsv(h, s, v);
            String hex = ColorFormatter.toHex(awt);
            Session session = current(sender);
            session.mode = Mode.SOLID;
            session.stops = List.of(new Stop(0f, hex));
            if (args.hasNext()) {
                session.text = args.rest();
            }
            preview(sender, session, session.text);
        } catch (NumberFormatException e) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>Hue, saturation and value must be numbers."));
        }
    }

    private void apply(CommandSender sender, Args args, String label) {
        if (args.remaining() < 2) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>Usage: /" + label + " color apply <prefix|suffix> <user|g:group> [priority]"));
            return;
        }
        String kind = args.next().toLowerCase(Locale.ROOT);
        boolean prefix = kind.equals("prefix") || kind.equals("pre");
        if (!prefix && !kind.equals("suffix") && !kind.equals("suf")) {
            sender.sendMessage(Text.parse("<gray>[<gold>RexRanks<gray>] <red>Use prefix or suffix."));
            return;
        }
        String target = args.next();
        int priority = 100;
        if (args.hasNext()) {
            try {
                priority = Integer.parseInt(args.next());
            } catch (NumberFormatException ignored) {
            }
        }
        Session session = current(sender);
        String mm = session.miniMessage();
        Node node = prefix ? Node.prefix(priority, mm) : Node.suffix(priority, mm);
        if (target.toLowerCase(Locale.ROOT).startsWith("g:") || target.toLowerCase(Locale.ROOT).startsWith("group:")) {
            String name = target.substring(target.indexOf(':') + 1);
            GroupImpl group = plugin.storage().getGroup(name).orElse(null);
            if (group == null) {
                plugin.messages().send(sender, "group-not-found", "group", name);
                return;
            }
            group.data().addOrReplace(node);
            plugin.groupManager().saveGroup(group);
            plugin.invalidateAllCaches();
            plugin.messages().send(sender, prefix ? "color.applied-prefix" : "color.applied-suffix",
                    "target", "group " + group.getName(), "preview", mm);
            return;
        }
        plugin.userManager().lookupUniqueId(target).thenCompose(id -> {
            java.util.UUID uuid = id.orElse(null);
            if (uuid == null && sender instanceof Player player && target.equalsIgnoreCase(player.getName())) {
                uuid = player.getUniqueId();
            }
            if (uuid == null) {
                uuid = plugin.userManager().offlineUuid(target);
            }
            return plugin.userManager().loadUser(uuid, target);
        }).whenComplete((user, error) -> plugin.schedulers().runSync(() -> {
            if (error != null || user == null) {
                plugin.messages().send(sender, "player-not-found", "player", target);
                return;
            }
            UserImpl impl = (UserImpl) user;
            impl.data().addOrReplace(node);
            plugin.userManager().saveUser(impl);
            plugin.messages().send(sender, prefix ? "color.applied-prefix" : "color.applied-suffix",
                    "target", impl.getUsername(), "preview", mm);
        }));
    }

    void preview(CommandSender sender, Session session, String text) {
        String mm = session.miniMessage(text);
        String legacy = session.legacy(text);
        plugin.messages().send(sender, "color.preview-header");
        sender.sendMessage(Text.parse("  <gray>Preview: ").append(Text.parse(mm)));
        sender.sendMessage(copyLine("MiniMessage", mm));
        sender.sendMessage(copyLine("Legacy hex", legacy));
        sender.sendMessage(Text.parse("  <gray>Apply: <gold>/rr color apply prefix <user> <gray>or <gold>suffix"));
    }

    private Component copyLine(String label, String value) {
        return Text.parse("  <gray>" + label + ": <white>" + Text.escape(value))
                .clickEvent(ClickEvent.copyToClipboard(value))
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>Click to copy")));
    }

    private void openGui(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, Text.parse("<gold>" + GUI_TITLE));
        Session session = current(player);
        for (int i = 0; i < SPECTRUM.length && i < 18; i++) {
            inventory.setItem(i, dyed(SPECTRUM[i], session));
        }
        ItemStack preview = dyed(session.primaryRgb(), session);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(session.miniMessage()));
            meta.lore(List.of(
                    Text.parse("<gray>Current mode: <white>" + session.mode.name().toLowerCase(Locale.ROOT)),
                    Text.parse("<gray>Left-click a swatch to set a solid colour."),
                    Text.parse("<gray>Shift-click to add a gradient stop."),
                    Text.parse("<gray>Output is also printed in chat.")
            ));
            preview.setItemMeta(meta);
        }
        inventory.setItem(22, preview);
        player.openInventory(inventory);
        openGuis.put(player.getUniqueId(), inventory);
        preview(player, session, session.text);
    }

    private ItemStack dyed(int rgb, Session session) {
        ItemStack stack = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        meta.setColor(Color.fromRGB(r, g, b));
        String hex = String.format("#%02x%02x%02x", r, g, b);
        meta.displayName(Text.parse(ColorFormatter.solidMiniMessage(hex, hex)));
        meta.lore(List.of(Text.parse("<gray>Click to use " + hex)));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory inventory = openGuis.get(player.getUniqueId());
        if (inventory == null || event.getInventory() != inventory) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.LEATHER_HELMET) {
            return;
        }
        if (!(current.getItemMeta() instanceof LeatherArmorMeta leather)) {
            return;
        }
        Color colour = leather.getColor();
        String hex = String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
        Session session = current(player);
        if (event.isShiftClick()) {
            List<Stop> stops = new ArrayList<>(session.stops);
            stops.add(new Stop(1f, hex));
            // Re-space
            for (int i = 0; i < stops.size(); i++) {
                float pos = stops.size() == 1 ? 0f : (float) i / (float) (stops.size() - 1);
                stops.set(i, new Stop(pos, stops.get(i).hex));
            }
            session.stops = stops;
            session.mode = stops.size() > 1 ? Mode.GRADIENT : Mode.SOLID;
        } else {
            session.mode = Mode.SOLID;
            session.stops = List.of(new Stop(0f, hex));
        }
        preview(player, session, session.text);
        openGui(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }

    private Session current(CommandSender sender) {
        UUID id = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        return sessions.computeIfAbsent(id, k -> new Session());
    }

    private static boolean looksLikeHex(String token) {
        String t = token.startsWith("&#") ? token.substring(2) : token.startsWith("#") ? token.substring(1) : token;
        return t.length() == 6 && t.chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    private static String[] join(String first, String rest) {
        if (rest == null || rest.isBlank()) {
            return new String[]{first};
        }
        return (first + " " + rest).split(" ");
    }

    private static List<String> filter(String prefix, String... options) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    private enum Mode { SOLID, GRADIENT, RAINBOW }

    private static final class Stop {
        final float position;
        final String hex;

        Stop(float position, String hex) {
            this.position = position;
            this.hex = hex;
        }
    }

    static final class Session {
        Mode mode = Mode.SOLID;
        List<Stop> stops = List.of(new Stop(0f, "#ffd36a"));
        String text = "RexRanks";
        boolean backwards;

        String miniMessage() {
            return miniMessage(text);
        }

        String miniMessage(String sample) {
            String t = sample == null || sample.isBlank() ? "RexRanks" : sample;
            return switch (mode) {
                case SOLID -> ColorFormatter.solidMiniMessage(stops.getFirst().hex, t);
                case GRADIENT -> ColorFormatter.gradientMiniMessage(stops.stream().map(s -> s.hex).toList(), t);
                case RAINBOW -> ColorFormatter.rainbowMiniMessage(t, backwards, false);
            };
        }

        String legacy(String sample) {
            String t = sample == null || sample.isBlank() ? "RexRanks" : sample;
            return switch (mode) {
                case SOLID -> ColorFormatter.solidLegacy(stops.getFirst().hex, t);
                case GRADIENT -> {
                    List<GradientStop> gs = new ArrayList<>();
                    for (Stop stop : stops) {
                        gs.add(new GradientStop(stop.position, ColorFormatter.parseHex(stop.hex)));
                    }
                    yield ColorFormatter.gradientLegacy(gs, t);
                }
                case RAINBOW -> ColorFormatter.rainbowLegacy(t, backwards, 1f, 1f);
            };
        }

        int primaryRgb() {
            try {
                java.awt.Color c = ColorFormatter.parseHex(stops.getFirst().hex);
                return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
            } catch (Exception e) {
                return 0xFFD36A;
            }
        }
    }
}
