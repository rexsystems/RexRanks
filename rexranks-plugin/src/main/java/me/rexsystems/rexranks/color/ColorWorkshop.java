package me.rexsystems.rexranks.color;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.util.Args;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * In-game colour / gradient workshop. Full implementation lands in a follow-up commit.
 */
public final class ColorWorkshop implements Listener {
    private final RexRanksPlugin plugin;

    public ColorWorkshop(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, Args args, String label) {
        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "The colour workshop is still being wired up. Use the web editor's colour studio in the meantime."));
    }

    public List<String> complete(String[] raw, String last) {
        String prefix = last == null ? "" : last.toLowerCase();
        return java.util.stream.Stream.of("solid", "gradient", "rainbow", "hsv", "apply", "gui")
                .filter(s -> s.startsWith(prefix))
                .toList();
    }
}
