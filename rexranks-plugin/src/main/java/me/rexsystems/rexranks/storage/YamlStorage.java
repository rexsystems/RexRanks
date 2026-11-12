package me.rexsystems.rexranks.storage;

import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class YamlStorage implements Storage {
    private final Path root;
    private final StorageContext context;
    private final Logger logger;
    private final Yaml yaml;
    private final ConcurrentHashMap<UUID, UserImpl> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GroupImpl> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrackImpl> tracks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> uuidCache = new ConcurrentHashMap<>();

    public YamlStorage(Path root, StorageContext context, Logger logger) {
        this.root = root;
        this.context = context;
        this.logger = logger;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        LoaderOptions loader = new LoaderOptions();
        loader.setCodePointLimit(10 * 1024 * 1024);
        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        this.yaml = new Yaml(new SafeConstructor(loader), representer, options);
    }

    @Override
    public String name() {
        return "yaml";
    }

    @Override
    public void init() throws Exception {
        Files.createDirectories(usersDir());
        Files.createDirectories(groupsDir());
        if (!Files.exists(tracksFile())) {
            writeYaml(tracksFile(), Map.of("tracks", Map.of()));
        }
        if (!Files.exists(uuidFile())) {
            writeYaml(uuidFile(), Map.of("mappings", Map.of()));
        }
    }

    @Override
    public synchronized void loadAll() throws Exception {
        users.clear();
        groups.clear();
        tracks.clear();
        uuidCache.clear();

        if (Files.isDirectory(groupsDir())) {
            try (var stream = Files.list(groupsDir())) {
                for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                    try {
                        GroupImpl group = readGroup(file);
                        groups.put(group.getName(), group);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to read group file " + file, e);
                    }
                }
            }
        }
        if (!groups.containsKey(context.defaultGroup())) {
            GroupImpl def = context.newGroup(context.defaultGroup());
            def.setWeight(0);
            groups.put(def.getName(), def);
            saveGroup(def);
        }
        if (Files.isDirectory(usersDir())) {
            try (var stream = Files.list(usersDir())) {
                for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                    try {
                        UserImpl user = readUser(file);
                        users.put(user.getUniqueId(), user);
                        uuidCache.put(user.getUsername().toLowerCase(Locale.ROOT), user.getUniqueId());
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to read user file " + file, e);
                    }
                }
            }
        }
        loadTracks();
        loadUuidCache();
    }

    @SuppressWarnings("unchecked")
    private UserImpl readUser(Path file) throws IOException {
        Map<String, Object> map = readYaml(file);
        UUID uuid = UUID.fromString(String.valueOf(map.get("uuid")));
        String name = String.valueOf(map.getOrDefault("name", uuid.toString()));
        String primary = String.valueOf(map.getOrDefault("primary-group", context.defaultGroup()));
        UserImpl user = context.newUser(uuid, name, primary);
        List<Node> nodes = NodeCodec.fromMaps(map.get("nodes"));
        user.dataImpl().replaceAll(nodes);
        user.setUpdatedAt(asLong(map.get("updated-at")));
        return user;
    }

    @SuppressWarnings("unchecked")
    private GroupImpl readGroup(Path file) throws IOException {
        Map<String, Object> map = readYaml(file);
        String name = String.valueOf(map.getOrDefault("name", file.getFileName().toString().replace(".yml", "")));
        GroupImpl group = context.newGroup(name);
        group.dataImpl().replaceAll(NodeCodec.fromMaps(map.get("nodes")));
        group.setUpdatedAt(asLong(map.get("updated-at")));
        return group;
    }

    @SuppressWarnings("unchecked")
    private void loadTracks() throws IOException {
        if (!Files.exists(tracksFile())) {
            return;
        }
        Map<String, Object> map = readYaml(tracksFile());
        Object raw = map.get("tracks");
        if (raw instanceof Map<?, ?> trackMap) {
            trackMap.forEach((k, v) -> {
                TrackImpl track = context.newTrack(String.valueOf(k));
                List<String> groups = new ArrayList<>();
                if (v instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        groups.add(String.valueOf(item));
                    }
                }
                track.setGroups(groups);
                tracks.put(track.getName(), track);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void loadUuidCache() throws IOException {
        if (!Files.exists(uuidFile())) {
            return;
        }
        Map<String, Object> map = readYaml(uuidFile());
        Object raw = map.get("mappings");
        if (raw instanceof Map<?, ?> mappings) {
            mappings.forEach((k, v) -> {
                try {
                    uuidCache.put(String.valueOf(k).toLowerCase(Locale.ROOT), UUID.fromString(String.valueOf(v)));
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
    }

    @Override
    public Collection<UserImpl> getLoadedUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    @Override
    public Collection<GroupImpl> getLoadedGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    @Override
    public Collection<TrackImpl> getLoadedTracks() {
        return Collections.unmodifiableCollection(tracks.values());
    }

    @Override
    public Optional<UserImpl> getUser(UUID uniqueId) {
        return Optional.ofNullable(users.get(uniqueId));
    }

    @Override
    public Optional<GroupImpl> getGroup(String name) {
        return Optional.ofNullable(groups.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<TrackImpl> getTrack(String name) {
        return Optional.ofNullable(tracks.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public synchronized void saveUser(UserImpl user) throws Exception {
        users.put(user.getUniqueId(), user);
        uuidCache.put(user.getUsername().toLowerCase(Locale.ROOT), user.getUniqueId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", user.getUniqueId().toString());
        map.put("name", user.getUsername());
        map.put("primary-group", user.getPrimaryGroup());
        map.put("updated-at", user.getUpdatedAt());
        map.put("nodes", NodeCodec.toMaps(user.data().toSet()));
        writeYaml(usersDir().resolve(user.getUniqueId() + ".yml"), map);
        saveUuidCache();
    }

    @Override
    public synchronized void saveGroup(GroupImpl group) throws Exception {
        groups.put(group.getName(), group);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", group.getName());
        map.put("updated-at", group.getUpdatedAt());
        map.put("nodes", NodeCodec.toMaps(group.data().toSet()));
        writeYaml(groupsDir().resolve(group.getName() + ".yml"), map);
    }

    @Override
    public synchronized void saveTrack(TrackImpl track) throws Exception {
        tracks.put(track.getName(), track);
        writeTracks();
    }

    @Override
    public synchronized boolean deleteUser(UUID uniqueId) throws Exception {
        UserImpl removed = users.remove(uniqueId);
        Files.deleteIfExists(usersDir().resolve(uniqueId + ".yml"));
        uuidCache.values().removeIf(uniqueId::equals);
        saveUuidCache();
        return removed != null;
    }

    @Override
    public synchronized boolean deleteGroup(String name) throws Exception {
        GroupImpl removed = groups.remove(name.toLowerCase(Locale.ROOT));
        Files.deleteIfExists(groupsDir().resolve(name.toLowerCase(Locale.ROOT) + ".yml"));
        return removed != null;
    }

    @Override
    public synchronized boolean deleteTrack(String name) throws Exception {
        TrackImpl removed = tracks.remove(name.toLowerCase(Locale.ROOT));
        writeTracks();
        return removed != null;
    }

    @Override
    public Map<String, UUID> uuidCache() {
        return Collections.unmodifiableMap(uuidCache);
    }

    @Override
    public synchronized void saveUuidMapping(String username, UUID uniqueId) throws Exception {
        uuidCache.put(username.toLowerCase(Locale.ROOT), uniqueId);
        saveUuidCache();
    }

    @Override
    public Collection<UUID> allUserIds() {
        return List.copyOf(users.keySet());
    }

    @Override
    public Collection<UserImpl> usersUpdatedSince(long since) {
        return users.values().stream().filter(u -> u.getUpdatedAt() > since).toList();
    }

    @Override
    public Collection<GroupImpl> groupsUpdatedSince(long since) {
        return groups.values().stream().filter(g -> g.getUpdatedAt() > since).toList();
    }

    @Override
    public long maxUpdatedAt() {
        long max = 0;
        for (UserImpl user : users.values()) {
            max = Math.max(max, user.getUpdatedAt());
        }
        for (GroupImpl group : groups.values()) {
            max = Math.max(max, group.getUpdatedAt());
        }
        return max;
    }

    @Override
    public void close() {
        // YAML is file-backed; nothing to shut down.
    }

    private void writeTracks() throws IOException {
        Map<String, Object> trackMap = new LinkedHashMap<>();
        tracks.values().forEach(t -> trackMap.put(t.getName(), t.getGroups()));
        writeYaml(tracksFile(), Map.of("tracks", trackMap));
    }

    private void saveUuidCache() throws IOException {
        Map<String, Object> mappings = new LinkedHashMap<>();
        uuidCache.forEach((k, v) -> mappings.put(k, v.toString()));
        writeYaml(uuidFile(), Map.of("mappings", mappings));
    }

    private Path usersDir() {
        return root.resolve("users");
    }

    private Path groupsDir() {
        return root.resolve("groups");
    }

    private Path tracksFile() {
        return root.resolve("tracks.yml");
    }

    private Path uuidFile() {
        return root.resolve("uuidcache.yml");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return new LinkedHashMap<>();
        }
    }

    private void writeYaml(Path file, Map<String, Object> data) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            yaml.dump(data, writer);
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return System.currentTimeMillis();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }
}
