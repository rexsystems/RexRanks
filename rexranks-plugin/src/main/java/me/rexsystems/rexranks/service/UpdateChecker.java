package me.rexsystems.rexranks.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Checks GitHub releases for a newer RexRanks version and optionally
 * notifies operators when they join.
 */
public final class UpdateChecker implements Listener {
    private static final String API = "https://api.github.com/repos/rexsystems/RexRanks/releases/latest";
    private static final String PAGE = "https://github.com/rexsystems/RexRanks/releases";

    private final RexRanksPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private volatile String latest;
    private volatile boolean updateAvailable;

    public UpdateChecker(RexRanksPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.config().updateChecker()) {
            return;
        }
        plugin.schedulers().runAsync(this::check);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String latest() {
        return latest;
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "RexRanks-UpdateChecker")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
            if (tag == null || tag.isBlank()) {
                return;
            }
            this.latest = tag.startsWith("v") ? tag.substring(1) : tag;
            String current = plugin.getPluginMeta().getVersion();
            if (isNewer(this.latest, current)) {
                this.updateAvailable = true;
                plugin.getLogger().info("A new version of RexRanks is available: " + this.latest
                        + " (current: " + current + "). " + PAGE);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Update check failed: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable || !plugin.config().updateNotifyOps()) {
            return;
        }
        Player player = event.getPlayer();
        String permission = plugin.config().updateNotifyPermission();
        if (!player.isOp() && (permission == null || permission.isBlank() || !player.hasPermission(permission))) {
            return;
        }
        String message = plugin.config().updateNotifyMessage()
                .replace("{latest}", latest == null ? "?" : latest)
                .replace("{current}", plugin.getPluginMeta().getVersion())
                .replace("%prefix%", "");
        plugin.schedulers().runOnEntity(player, () -> player.sendMessage(Text.parse(message)));
    }

    static boolean isNewer(String latest, String current) {
        if (latest == null || current == null) {
            return false;
        }
        String a = clean(latest);
        String b = clean(current);
        if (a.equalsIgnoreCase(b)) {
            return false;
        }
        String[] la = a.split("\\.");
        String[] lb = b.split("\\.");
        int n = Math.max(la.length, lb.length);
        for (int i = 0; i < n; i++) {
            int ia = i < la.length ? segment(la[i]) : 0;
            int ib = i < lb.length ? segment(lb[i]) : 0;
            if (ia > ib) {
                return true;
            }
            if (ia < ib) {
                return false;
            }
        }
        return false;
    }

    private static String clean(String version) {
        String v = version.trim().replaceFirst("^[vV]", "");
        int cut = v.indexOf('-');
        if (cut > 0) {
            v = v.substring(0, cut);
        }
        int plus = v.indexOf('+');
        if (plus > 0) {
            v = v.substring(0, plus);
        }
        return v;
    }

    private static int segment(String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
