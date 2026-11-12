package me.rexsystems.rexranks.importing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.rexsystems.rexranks.api.group.Group;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.storage.NodeCodec;
import me.rexsystems.rexranks.storage.Storage;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;

/**
 * Writes a RexRanks JSON export that LuckPerms-style tools can also read
 * (groups/users/tracks with a {@code nodes} array of shorthand or objects).
 */
public final class DataExporter {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public JsonObject exportAll(String version, Storage storage) {
        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("plugin", "RexRanks");
        meta.addProperty("version", version);
        meta.addProperty("generated", Instant.now().toString());
        root.add("metadata", meta);

        JsonObject groups = new JsonObject();
        for (Group group : storage.getLoadedGroups()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", group.getName());
            obj.addProperty("weight", group.getWeight());
            obj.addProperty("display-name", group.getDisplayName());
            obj.add("nodes", nodesJson(group.getNodes()));
            groups.add(group.getName(), obj);
        }
        root.add("groups", groups);

        JsonObject users = new JsonObject();
        JsonObject uuids = new JsonObject();
        for (User user : storage.getLoadedUsers()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", user.getUsername());
            obj.addProperty("primary-group", user.getPrimaryGroup());
            obj.add("nodes", nodesJson(user.getNodes()));
            users.add(user.getUniqueId().toString(), obj);
            uuids.addProperty(user.getUniqueId().toString(), user.getUsername());
        }
        root.add("users", users);
        root.add("uuids", uuids);

        JsonObject tracks = new JsonObject();
        for (Track track : storage.getLoadedTracks()) {
            JsonObject obj = new JsonObject();
            JsonArray groupsArray = new JsonArray();
            track.getGroups().forEach(groupsArray::add);
            obj.add("groups", groupsArray);
            tracks.add(track.getName(), obj);
        }
        root.add("tracks", tracks);
        return root;
    }

    public void write(String version, Storage storage, Path file) throws Exception {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(exportAll(version, storage), writer);
        }
    }

    private JsonArray nodesJson(Collection<Node> nodes) {
        JsonArray array = new JsonArray();
        for (Node node : nodes) {
            array.add(gson.toJsonTree(NodeCodec.toMap(node)));
        }
        return array;
    }
}
