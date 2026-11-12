package me.rexsystems.rexranks.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.importing.LuckPermsImporter;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.storage.NodeCodec;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Serialises the live data set for the web editor and applies a proposed
 * payload after computing a human-readable diff.
 */
public final class EditorPayload {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Storage storage;
    private final StorageContext context;

    public EditorPayload(Storage storage, StorageContext context) {
        this.storage = storage;
        this.context = context;
    }

    public JsonObject snapshot(String focus) {
        JsonObject root = new JsonObject();
        root.addProperty("focus", focus == null ? "all" : focus);
        JsonObject groups = new JsonObject();
        for (GroupImpl group : storage.getLoadedGroups()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", group.getName());
            obj.addProperty("weight", group.getWeight());
            obj.addProperty("displayName", group.getDisplayName());
            obj.add("nodes", nodesJson(group.data().toSet()));
            groups.add(group.getName(), obj);
        }
        root.add("groups", groups);

        JsonObject users = new JsonObject();
        for (UserImpl user : storage.getLoadedUsers()) {
            if (focus != null && focus.startsWith("user:") && !focus.substring(5).equalsIgnoreCase(user.getUniqueId().toString())
                    && !focus.substring(5).equalsIgnoreCase(user.getUsername())) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", user.getUniqueId().toString());
            obj.addProperty("name", user.getUsername());
            obj.addProperty("primaryGroup", user.getPrimaryGroup());
            obj.add("nodes", nodesJson(user.data().toSet()));
            users.add(user.getUniqueId().toString(), obj);
        }
        root.add("users", users);

        JsonObject tracks = new JsonObject();
        for (TrackImpl track : storage.getLoadedTracks()) {
            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            track.getGroups().forEach(arr::add);
            obj.add("groups", arr);
            tracks.add(track.getName(), obj);
        }
        root.add("tracks", tracks);
        return root;
    }

    public JsonObject diff(JsonObject proposed) {
        JsonObject current = snapshot("all");
        JsonObject diff = new JsonObject();
        diff.add("groups", subjectDiff(current.getAsJsonObject("groups"), proposed.has("groups") ? proposed.getAsJsonObject("groups") : new JsonObject()));
        diff.add("users", subjectDiff(current.getAsJsonObject("users"), proposed.has("users") ? proposed.getAsJsonObject("users") : new JsonObject()));
        diff.add("tracks", subjectDiff(current.getAsJsonObject("tracks"), proposed.has("tracks") ? proposed.getAsJsonObject("tracks") : new JsonObject()));
        JsonArray deletedGroups = new JsonArray();
        JsonArray deletedUsers = new JsonArray();
        JsonArray deletedTracks = new JsonArray();
        if (proposed.has("deleted") && proposed.get("deleted").isJsonObject()) {
            JsonObject del = proposed.getAsJsonObject("deleted");
            if (del.has("groups")) del.getAsJsonArray("groups").forEach(deletedGroups::add);
            if (del.has("users")) del.getAsJsonArray("users").forEach(deletedUsers::add);
            if (del.has("tracks")) del.getAsJsonArray("tracks").forEach(deletedTracks::add);
        }
        JsonObject deleted = new JsonObject();
        deleted.add("groups", deletedGroups);
        deleted.add("users", deletedUsers);
        deleted.add("tracks", deletedTracks);
        diff.add("deleted", deleted);
        int changes = count(diff.getAsJsonObject("groups")) + count(diff.getAsJsonObject("users"))
                + count(diff.getAsJsonObject("tracks")) + deletedGroups.size() + deletedUsers.size() + deletedTracks.size();
        diff.addProperty("changes", changes);
        return diff;
    }

    public String apply(JsonObject proposed) throws Exception {
        JsonObject d = diff(proposed);
        if (proposed.has("groups") && proposed.get("groups").isJsonObject()) {
            for (var entry : proposed.getAsJsonObject("groups").entrySet()) {
                GroupImpl group = storage.getGroup(entry.getKey()).orElseGet(() -> context.newGroup(entry.getKey()));
                List<Node> nodes = nodesFrom(entry.getValue());
                group.dataImpl().replaceAll(nodes);
                storage.saveGroup(group);
            }
        }
        if (proposed.has("users") && proposed.get("users").isJsonObject()) {
            for (var entry : proposed.getAsJsonObject("users").entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                JsonObject obj = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : uuid.toString();
                String primary = obj.has("primaryGroup") ? obj.get("primaryGroup").getAsString()
                        : obj.has("primary-group") ? obj.get("primary-group").getAsString() : context.defaultGroup();
                UserImpl user = storage.getUser(uuid).orElseGet(() -> context.newUser(uuid, name, primary));
                user.setUsername(name);
                user.setPrimaryGroup(primary);
                user.dataImpl().replaceAll(nodesFrom(entry.getValue()));
                storage.saveUser(user);
            }
        }
        if (proposed.has("tracks") && proposed.get("tracks").isJsonObject()) {
            for (var entry : proposed.getAsJsonObject("tracks").entrySet()) {
                TrackImpl track = storage.getTrack(entry.getKey()).orElseGet(() -> context.newTrack(entry.getKey()));
                List<String> names = new ArrayList<>();
                JsonElement value = entry.getValue();
                JsonArray arr;
                if (value.isJsonArray()) arr = value.getAsJsonArray();
                else if (value.isJsonObject() && value.getAsJsonObject().has("groups")) arr = value.getAsJsonObject().getAsJsonArray("groups");
                else continue;
                arr.forEach(el -> names.add(el.getAsString()));
                track.setGroups(names);
                storage.saveTrack(track);
            }
        }
        if (proposed.has("deleted") && proposed.get("deleted").isJsonObject()) {
            JsonObject del = proposed.getAsJsonObject("deleted");
            if (del.has("groups")) {
                for (JsonElement el : del.getAsJsonArray("groups")) {
                    if (!el.getAsString().equalsIgnoreCase(context.defaultGroup())) {
                        storage.deleteGroup(el.getAsString());
                    }
                }
            }
            if (del.has("users")) {
                for (JsonElement el : del.getAsJsonArray("users")) {
                    try { storage.deleteUser(UUID.fromString(el.getAsString())); } catch (IllegalArgumentException ignored) {}
                }
            }
            if (del.has("tracks")) {
                for (JsonElement el : del.getAsJsonArray("tracks")) {
                    storage.deleteTrack(el.getAsString());
                }
            }
        }
        return d.get("changes").getAsInt() + " change(s) applied";
    }

    private JsonArray nodesJson(Set<Node> nodes) {
        JsonArray array = new JsonArray();
        for (Node node : nodes) {
            array.add(gson.toJsonTree(NodeCodec.toMap(node)));
        }
        return array;
    }

    private List<Node> nodesFrom(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return LuckPermsImporter.extractNodes(element, new me.rexsystems.rexranks.importing.ImportResult());
        }
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("nodes")) {
            return LuckPermsImporter.extractNodes(obj, new me.rexsystems.rexranks.importing.ImportResult());
        }
        return List.of();
    }

    private JsonObject subjectDiff(JsonObject current, JsonObject proposed) {
        JsonObject out = new JsonObject();
        Set<String> keys = new LinkedHashSet<>();
        current.keySet().forEach(keys::add);
        proposed.keySet().forEach(keys::add);
        for (String key : keys) {
            boolean inCurrent = current.has(key);
            boolean inProposed = proposed.has(key);
            JsonObject entry = new JsonObject();
            if (inProposed && !inCurrent) {
                entry.addProperty("status", "added");
                entry.add("after", proposed.get(key));
                out.add(key, entry);
            } else if (inCurrent && !inProposed) {
                // proposed snapshot may be partial — only flag as removed if listed under deleted
            } else if (inCurrent && inProposed && !current.get(key).equals(proposed.get(key))) {
                entry.addProperty("status", "changed");
                entry.add("before", current.get(key));
                entry.add("after", proposed.get(key));
                out.add(key, entry);
            }
        }
        return out;
    }

    private int count(JsonObject object) {
        return object == null ? 0 : object.size();
    }
}
