package me.rexsystems.rexranks.hook;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.papi.RexRanksExpansion;

public final class PapiHook {
    private PapiHook() {
    }

    public static void register(RexRanksPlugin plugin) {
        new RexRanksExpansion(plugin).register();
        plugin.getLogger().info("PlaceholderAPI expansion registered.");
    }
}
