package me.rexsystems.rexranks.importing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.engine.NodeShorthand;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Imports a LuckPerms JSON export ({@code /lp export}) or web-editor dump.
 * Supports both object-shaped and shorthand-string node lists, and the older
 * {@code permissions} array used by some third-party dumps.
 */
public final class LuckPermsImporter {
    private final Storage storage;
    private final StorageContext context;

    public LuckPermsImporter(Storage storage, StorageContext context) {
        this.storage = storage;
        this.context = context;
    }

    public ImportResult importFile(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return importJson(JsonParser.parseReader(reader));
        }
    }

    public ImportResult importJson(JsonElement root) throws Exception {
        ImportResult result = new ImportResult();
        if (!root.isJsonObject()) {
            result.warn("Root of the export is not a JSON object");
            return result;
        }
        JsonObject object = root.getAsJsonObject();
        if (object.has("groups") && object.get("groups").isJsonObject()) {
            importGroups(object.getAsJsonObject("groups"), result);
        }
        if (object.has("tracks") && object.get("tracks").isJsonObject()) {
            importTracks(object.getAsJsonObject("tracks"), result);
        }
        if (object.has("users") && object.get("users").isJsonObject()) {
            importUsers(object.getAsJsonObject("users"), object, result);
        }
        return result;
    }

    private void importGroups(JsonObject groups, ImportResult result) throws Exception {
        for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            GroupImpl group = storage.getGroup(name).orElseGet(() -> context.newGroup(name));
            List<Node> nodes = extractNodes(entry.getValue(), result);
            if (nodes.isEmpty() && entry.getValue().isJsonObject()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("displayName")) {
                    nodes.add(Node.displayName(obj.get("displayName").getAsString()));
                }
            }
            group.dataImpl().replaceAll(nodes);
            storage.saveGroup(group);
            result.addGroup();
            result.addNodes(nodes.size());
        }
    }

    private void importTracks(JsonObject tracks, ImportResult result) throws Exception {
        for (Map.Entry<String, JsonElement> entry : tracks.entrySet()) {
            TrackImpl track = storage.getTrack(entry.getKey()).orElseGet(() -> context.newTrack(entry.getKey()));
            List<String> names = new ArrayList<>();
            JsonElement value = entry.getValue();
            JsonArray array;
            if (value.isJsonArray()) {
                array = value.getAsJsonArray();
            } else if (value.isJsonObject() && value.getAsJsonObject().has("groups")) {
                array = value.getAsJsonObject().getAsJsonArray("groups");
            } else {
                result.warn("Track '" + entry.getKey() + "' has an unrecognised shape");
                continue;
            }
            for (JsonElement element : array) {
                names.add(element.isJsonPrimitive() ? element.getAsString() : element.toString());
            }
            track.setGroups(names);
            storage.saveTrack(track);
            result.addTrack();
        }
    }

    private void importUsers(JsonObject users, JsonObject root, ImportResult result) throws Exception {
        JsonObject uuidNames = root.has("uuids") && root.get("uuids").isJsonObject()
                ? root.getAsJsonObject("uuids")
                : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                result.warn("Skipping user with invalid UUID: " + entry.getKey());
                continue;
            }
            String username = uuid.toString();
            if (uuidNames.has(entry.getKey())) {
                username = uuidNames.get(entry.getKey()).getAsString();
            }
            String primary = "default";
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                if (obj.has("name")) {
                    username = obj.get("name").getAsString();
                } else if (obj.has("username")) {
                    username = obj.get("username").getAsString();
                }
                if (obj.has("primary-group")) {
                    primary = obj.get("primary-group").getAsString();
                } else if (obj.has("primaryGroup")) {
                    primary = obj.get("primaryGroup").getAsString();
                } else if (obj.has("primarygroup")) {
                    primary = obj.get("primarygroup").getAsString();
                }
            }
            UserImpl user = storage.getUser(uuid).orElseGet(() -> context.newUser(uuid, username, primary));
            user.setUsername(username);
            user.setPrimaryGroup(primary);
            List<Node> nodes = extractNodes(value, result);
            user.dataImpl().replaceAll(nodes);
            storage.saveUser(user);
            result.addUser();
            result.addNodes(nodes.size());
        }
    }

    public static List<Node> extractNodes(JsonElement value, ImportResult result) {
        List<Node> nodes = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return nodes;
        }
        if (value.isJsonArray()) {
            addAll(value.getAsJsonArray(), nodes, result);
            return nodes;
        }
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            if (obj.has("nodes")) {
                addAll(asArray(obj.get("nodes")), nodes, result);
            } else if (obj.has("permissions")) {
                addAll(asArray(obj.get("permissions")), nodes, result);
            }
        }
        return nodes;
    }

    private static JsonArray asArray(JsonElement element) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static void addAll(JsonArray array, List<Node> nodes, ImportResult result) {
        for (JsonElement element : array) {
            try {
                Node node = parseNode(element);
                if (node != null) {
                    nodes.add(node);
                }
            } catch (RuntimeException e) {
                result.warn("Skipped node " + element + ": " + e.getMessage());
            }
        }
    }

    public static Node parseNode(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return NodeShorthand.parse(element.getAsString());
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String key = firstString(obj, "key", "permission", "node");
        if (key == null) {
            return null;
        }
        boolean value = true;
        if (obj.has("value")) {
            JsonElement raw = obj.get("value");
            if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) {
                value = raw.getAsBoolean();
            } else if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()
                    && (raw.getAsString().equalsIgnoreCase("false") || raw.getAsString().equalsIgnoreCase("true"))) {
                value = Boolean.parseBoolean(raw.getAsString());
            }
        }
        long expiry = 0;
        if (obj.has("expiry")) {
            expiry = obj.get("expiry").getAsLong();
            // LuckPerms stores expiry as unix seconds if the value is 10 digits.
            if (expiry > 0 && expiry < 10_000_000_000L) {
                expiry *= 1000;
            }
        }
        ImmutableContextSet contexts = ImmutableContextSet.empty();
        JsonElement ctx = obj.has("contexts") ? obj.get("contexts") : obj.get("context");
        if (ctx != null && ctx.isJsonObject()) {
            ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
            ctx.getAsJsonObject().entrySet().forEach(e -> {
                if (e.getValue().isJsonArray()) {
                    e.getValue().getAsJsonArray().forEach(v -> builder.add(e.getKey(), v.getAsString()));
                } else if (e.getValue().isJsonPrimitive()) {
                    builder.add(e.getKey(), e.getValue().getAsString());
                }
            });
            contexts = builder.build();
        }
        // If the key is shorthand (prefix.N.x, group.x, ...) parse it, then apply expiry/contexts.
        Node parsed = NodeShorthand.parse(key, value, expiry, contexts);
        return parsed;
    }

    private static String firstString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }
}
