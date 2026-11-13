package me.rexsystems.rexranks.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.rexsystems.rexranks.RexRanksPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Embedded HTTP server that serves the web editor and a small authenticated REST API.
 */
public final class EditorServer {
    private final RexRanksPlugin plugin;
    private final EditorService service;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private HttpServer server;

    public EditorServer(RexRanksPlugin plugin, EditorService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start(String host, int port) throws IOException {
        stop();
        InetSocketAddress address = "0.0.0.0".equals(host) || host == null || host.isBlank()
                ? new InetSocketAddress(port)
                : new InetSocketAddress(host, port);
        server = HttpServer.create(address, 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "RexRanks-Editor");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        plugin.getLogger().info("Web editor listening on " + host + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isBlank()) path = "/";
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                cors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (path.startsWith("/api/")) {
                handleApi(exchange, path.substring(4)); // keep leading slash via [4] = "/..."
                return;
            }
            serveStatic(exchange, path);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Editor HTTP error", e);
            write(exchange, 500, "application/json", "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        // path like /session, /groups, ...
        if (path.startsWith("/api")) path = path.substring(4);
        if (path.isEmpty()) path = "/";
        String token = tokenOf(exchange);
        EditorSession session = service.authenticate(token);
        if (session == null) {
            write(exchange, 401, "application/json", "{\"error\":\"invalid or expired token\"}");
            return;
        }
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject response;
        switch (path) {
            case "/", "/session" -> {
                response = new JsonObject();
                response.addProperty("ok", true);
                response.addProperty("createdBy", session.createdBy);
                response.addProperty("focus", session.focus);
                response.addProperty("expiresAt", session.expiresAt);
                response.addProperty("server", plugin.config().serverName());
                response.addProperty("version", plugin.getPluginMeta().getVersion());
                response.add("data", service.payload().snapshot(session.focus));
                response.add("knownNodes", gson.toJsonTree(plugin.knownNodes().all()));
            }
            case "/data" -> response = service.payload().snapshot(session.focus);
            case "/diff" -> {
                JsonObject proposed = gson.fromJson(body, JsonObject.class);
                response = service.payload().diff(proposed);
                session.lastDiff = response;
            }
            case "/apply" -> {
                JsonObject proposed = gson.fromJson(body, JsonObject.class);
                session.pendingApply = proposed;
                session.lastDiff = service.payload().diff(proposed);
                String summary;
                try {
                    summary = service.applyProposed(proposed);
                } catch (Exception e) {
                    throw new IOException("Failed to apply editor payload", e);
                }
                response = new JsonObject();
                response.addProperty("ok", true);
                response.addProperty("summary", summary);
                response.add("diff", session.lastDiff);
            }
            case "/search" -> {
                String q = query(exchange, "q");
                response = service.search(q);
            }
            default -> {
                write(exchange, 404, "application/json", "{\"error\":\"not found\"}");
                return;
            }
        }
        write(exchange, 200, "application/json", gson.toJson(response));
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String relative = path.equals("/") ? "/index.html" : path;
        if (relative.contains("..")) {
            write(exchange, 400, "text/plain", "bad path");
            return;
        }
        String resource = "/web" + relative;
        InputStream stream = RexRanksPlugin.class.getResourceAsStream(resource);
        if (stream == null && !relative.contains(".")) {
            stream = RexRanksPlugin.class.getResourceAsStream("/web/index.html");
            relative = "/index.html";
        }
        if (stream == null) {
            write(exchange, 404, "text/plain", "not found");
            return;
        }
        byte[] data = stream.readAllBytes();
        stream.close();
        String mime = URLConnection.guessContentTypeFromName(relative);
        if (mime == null) {
            if (relative.endsWith(".js")) mime = "application/javascript";
            else if (relative.endsWith(".css")) mime = "text/css";
            else if (relative.endsWith(".svg")) mime = "image/svg+xml";
            else if (relative.endsWith(".html")) mime = "text/html; charset=utf-8";
            else if (relative.endsWith(".json")) mime = "application/json";
            else if (relative.endsWith(".woff2")) mime = "font/woff2";
            else mime = "application/octet-stream";
        }
        cors(exchange.getResponseHeaders());
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private void write(HttpExchange exchange, int status, String mime, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        cors(exchange.getResponseHeaders());
        exchange.getResponseHeaders().set("Content-Type", mime + (mime.contains("charset") ? "" : "; charset=utf-8"));
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private void cors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    }

    private String tokenOf(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return query(exchange, "token");
    }

    private String query(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            if (k.equals(key)) return v;
        }
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
