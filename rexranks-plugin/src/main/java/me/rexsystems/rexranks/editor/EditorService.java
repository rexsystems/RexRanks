package me.rexsystems.rexranks.editor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.rexsystems.rexranks.RexRanksPlugin;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.engine.NodeShorthand;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class EditorService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789abcdefghijkmnopqrstuvwxyz".toCharArray();
    private final RexRanksPlugin plugin;
    private final EditorPayload payload;
    private final EditorServer server;
    private final ConcurrentHashMap<String, EditorSession> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EditorSession> byCode = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public EditorService(RexRanksPlugin plugin) {
        this.plugin = plugin;
        this.payload = new EditorPayload(plugin.storage(), plugin.storageContext());
        this.server = new EditorServer(plugin, this);
    }

    public EditorPayload payload() {
        return payload;
    }

    public void start() {
        if (!plugin.config().editorEnabled()) return;
        if (!"integrated".equals(plugin.config().editorMode())) return;
        try {
            server.start(plugin.config().editorHost(), plugin.config().editorPort());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start the web editor on port " + plugin.config().editorPort(), e);
        }
    }

    public void stop() {
        server.stop();
    }

    public void openFull(CommandSender sender) {
        open(sender, "all");
    }

    public void openForUser(CommandSender sender, UserImpl user) {
        open(sender, "user:" + user.getUniqueId());
    }

    public void openForGroup(CommandSender sender, GroupImpl group) {
        open(sender, "group:" + group.getName());
    }

    public void open(CommandSender sender, String focus) {
        if (!plugin.config().editorEnabled()) {
            plugin.messages().send(sender, "editor.disabled");
            return;
        }
        purge();
        String token = randomString(24);
        String code = randomString(8);
        long expires = System.currentTimeMillis() + plugin.config().editorTokenMinutes() * 60_000L;
        EditorSession session = new EditorSession(token, code, sender.getName(), expires, focus);
        byToken.put(token, session);
        byCode.put(code.toLowerCase(), session);

        if ("remote".equals(plugin.config().editorMode())) {
            plugin.schedulers().runAsync(() -> {
                try {
                    String url = uploadRemote(session);
                    plugin.schedulers().runSync(() -> sendLink(sender, url, session));
                } catch (Exception e) {
                    plugin.schedulers().runSync(() -> plugin.messages().send(sender, "editor.apply-fail", "error",
                            "Remote editor upload failed: " + e.getMessage()));
                    plugin.getLogger().log(Level.WARNING, "Remote editor upload failed", e);
                }
            });
            return;
        }
        if (!server.isRunning()) {
            start();
        }
        sendLink(sender, publicUrl() + "/?token=" + token, session);
    }

    private void sendLink(CommandSender sender, String url, EditorSession session) {
        Component message = plugin.messages().component("editor.link", "url", url);
        Component clickable = Text.url("<gold><underlined>" + url, url,
                plugin.messages().raw("editor.link-hover", "&7Click to open.")
                        .replace("{minutes}", String.valueOf(plugin.config().editorTokenMinutes())));
        sender.sendMessage(message);
        sender.sendMessage(clickable);
        sender.sendMessage(Text.parse("<gray>Apply code (for remote sessions): <white>" + session.applyCode));
    }

    private String publicUrl() {
        String configured = plugin.config().editorPublicUrl();
        if (configured != null && !configured.isBlank()) {
            return trimSlash(configured);
        }
        String ip = Bukkit.getIp();
        if (ip == null || ip.isBlank() || ip.equals("0.0.0.0")) {
            ip = "127.0.0.1";
        }
        return "http://" + ip + ":" + plugin.config().editorPort();
    }

    private String uploadRemote(EditorSession session) throws Exception {
        String remote = trimSlash(plugin.config().editorRemoteUrl());
        JsonObject body = new JsonObject();
        body.addProperty("token", session.token);
        body.addProperty("applyCode", session.applyCode);
        body.addProperty("createdBy", session.createdBy);
        body.addProperty("expiresAt", session.expiresAt);
        body.addProperty("focus", session.focus);
        body.add("data", payload.snapshot(session.focus));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(remote + "/api/sessions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (json.has("url")) return json.get("url").getAsString();
        String id = json.has("id") ? json.get("id").getAsString() : session.token;
        return remote + "/#/s/" + id + "?token=" + session.token;
    }

    public EditorSession authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        purge();
        EditorSession session = byToken.get(token);
        if (session == null || session.expired()) return null;
        return session;
    }

    public void applyCode(CommandSender sender, String code) {
        purge();
        EditorSession session = byCode.get(code.toLowerCase());
        if (session == null || session.pendingApply == null) {
            // Try pulling from the remote backend.
            if ("remote".equals(plugin.config().editorMode())) {
                plugin.schedulers().runAsync(() -> {
                    try {
                        JsonObject proposed = fetchRemoteApply(code);
                        String summary = applyProposed(proposed);
                        plugin.schedulers().runSync(() -> plugin.messages().send(sender, "editor.applied", "code", code, "summary", summary));
                    } catch (Exception e) {
                        plugin.schedulers().runSync(() -> plugin.messages().send(sender, "editor.no-session"));
                    }
                });
                return;
            }
            plugin.messages().send(sender, "editor.no-session");
            return;
        }
        try {
            String summary = applyProposed(session.pendingApply);
            plugin.messages().send(sender, "editor.applied", "code", code, "summary", summary);
        } catch (Exception e) {
            plugin.messages().send(sender, "editor.apply-fail", "error", String.valueOf(e.getMessage()));
        }
    }

    public String applyProposed(JsonObject proposed) throws Exception {
        String summary = payload.apply(proposed);
        plugin.invalidateAllCaches();
        plugin.notifyChange("editor");
        return summary;
    }

    private JsonObject fetchRemoteApply(String code) throws Exception {
        String remote = trimSlash(plugin.config().editorRemoteUrl());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(remote + "/api/apply/" + code))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (json.has("payload")) return json.getAsJsonObject("payload");
        return json;
    }

    public JsonObject search(String query) {
        JsonObject root = new JsonObject();
        JsonArray hits = new JsonArray();
        String q = query == null ? "" : query.toLowerCase();
        for (UserImpl user : plugin.storage().getLoadedUsers()) {
            for (Node node : user.data().toSet()) {
                if (NodeShorthand.toShorthand(node).toLowerCase().contains(q)) {
                    JsonObject hit = new JsonObject();
                    hit.addProperty("subject", "user:" + user.getUsername());
                    hit.addProperty("node", NodeShorthand.toShorthand(node));
                    hits.add(hit);
                }
            }
        }
        for (GroupImpl group : plugin.storage().getLoadedGroups()) {
            for (Node node : group.data().toSet()) {
                if (NodeShorthand.toShorthand(node).toLowerCase().contains(q)) {
                    JsonObject hit = new JsonObject();
                    hit.addProperty("subject", "group:" + group.getName());
                    hit.addProperty("node", NodeShorthand.toShorthand(node));
                    hits.add(hit);
                }
            }
        }
        root.add("hits", hits);
        root.addProperty("count", hits.size());
        return root;
    }

    private void purge() {
        long now = System.currentTimeMillis();
        Iterator<EditorSession> it = byToken.values().iterator();
        while (it.hasNext()) {
            EditorSession session = it.next();
            if (session.expiresAt < now) {
                it.remove();
                byCode.remove(session.applyCode.toLowerCase());
            }
        }
    }

    private String randomString(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) return url.substring(0, url.length() - 1);
        return url;
    }
}
