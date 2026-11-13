package me.rexsystems.rexranks.hook;

import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.vault.RexVaultChat;
import me.rexsystems.rexranks.vault.RexVaultPermission;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.ServicePriority;

public final class VaultHook {
    private VaultHook() {
    }

    public static void register(RexRanksPlugin plugin) {
        RexVaultPermission permission = new RexVaultPermission(plugin);
        plugin.getServer().getServicesManager().register(Permission.class, permission, plugin, ServicePriority.High);
        plugin.getServer().getServicesManager().register(Chat.class, new RexVaultChat(plugin, permission), plugin, ServicePriority.High);
        plugin.getLogger().info("Vault permission and chat providers registered.");
    }
}
