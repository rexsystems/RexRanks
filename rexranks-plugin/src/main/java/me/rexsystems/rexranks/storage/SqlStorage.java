package me.rexsystems.rexranks.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.rexsystems.rexranks.api.context.Context;
import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

public final class SqlStorage implements Storage {
    private static final Type CONTEXT_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();
    private static final Type GROUP_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private final SqlDialect dialect;
    private final StorageContext context;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<UUID, UserImpl> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GroupImpl> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrackImpl> tracks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> uuidCache = new ConcurrentHashMap<>();

    public SqlStorage(SqlDialect dialect, HikariConfig hikari, StorageContext context, Logger logger) {
        this.dialect = dialect;
        this.context = context;
        this.logger = logger;
        if (dialect == SqlDialect.SQLITE) {
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
        }
        hikari.setPoolName("RexRanks-Hikari");
        this.dataSource = new HikariDataSource(hikari);
    }

    public static HikariConfig fileConfig(SqlDialect dialect, String filePath) {
        HikariConfig config = new HikariConfig();
        switch (dialect) {
            case H2 -> {
                config.setJdbcUrl("jdbc:h2:file:" + filePath + ";MODE=MYSQL;DB_CLOSE_ON_EXIT=FALSE");
                config.setDriverClassName("org.h2.Driver");
            }
            case SQLITE -> {
                config.setJdbcUrl("jdbc:sqlite:" + filePath);
                config.setDriverClassName("org.sqlite.JDBC");
            }
            default -> throw new IllegalArgumentException("fileConfig is only for H2/SQLite");
        }
        config.setMaximumPoolSize(dialect == SqlDialect.SQLITE ? 1 : 8);
        return config;
    }

    public static HikariConfig mysqlConfig(String host, int port, String database, String username, String password,
                                           int poolSize, String extra) {
        HikariConfig config = new HikariConfig();
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
        if (extra != null && !extra.isBlank()) {
            url = url + "&" + extra.replaceFirst("^&", "");
        }
        config.setJdbcUrl(url);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(2, poolSize));
        config.setMinimumIdle(1);
        return config;
    }

    public static HikariConfig memoryH2() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:rexranks_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(4);
        return config;
    }

    @Override
    public String name() {
        return dialect.name().toLowerCase(Locale.ROOT);
    }


    private String valueIdent() {
        return dialect.quote("value");
    }

    private String valueSelect() {
        return valueIdent() + " AS node_value";
    }

    @Override
    public void init() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_users (
                        uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        primary_group VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """.stripIndent());
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_user_nodes (
                        id %s,
                        uuid VARCHAR(36) NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        key_name VARCHAR(256) NOT NULL,
                        %s TEXT,
                        negated %s NOT NULL,
                        expiry BIGINT NOT NULL,
                        contexts TEXT NOT NULL
                    )
                    """.formatted(dialect.autoIncrementId(), valueIdent(), dialect.booleanType()).stripIndent());
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_groups (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        updated_at BIGINT NOT NULL
                    )
                    """.stripIndent());
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_group_nodes (
                        id %s,
                        name VARCHAR(64) NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        key_name VARCHAR(256) NOT NULL,
                        %s TEXT,
                        negated %s NOT NULL,
                        expiry BIGINT NOT NULL,
                        contexts TEXT NOT NULL
                    )
                    """.formatted(dialect.autoIncrementId(), valueIdent(), dialect.booleanType()).stripIndent());
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_tracks (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        groups_json TEXT NOT NULL
                    )
                    """.stripIndent());
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rexranks_uuid_cache (
                        username VARCHAR(16) NOT NULL PRIMARY KEY,
                        uuid VARCHAR(36) NOT NULL
                    )
                    """.stripIndent());
            try {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rexranks_user_nodes_uuid ON rexranks_user_nodes (uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rexranks_group_nodes_name ON rexranks_group_nodes (name)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rexranks_users_updated ON rexranks_users (updated_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rexranks_groups_updated ON rexranks_groups (updated_at)");
            } catch (SQLException e) {
                // MySQL older versions may not support IF NOT EXISTS on indexes.
                logger.log(Level.FINE, "Index creation: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void loadAll() throws Exception {
        users.clear();
        groups.clear();
        tracks.clear();
        uuidCache.clear();
        try (Connection connection = dataSource.getConnection()) {
            loadGroups(connection);
            loadUsers(connection);
            loadTracks(connection);
            loadUuid(connection);
        }
        if (!groups.containsKey(context.defaultGroup())) {
            GroupImpl def = context.newGroup(context.defaultGroup());
            def.setWeight(0);
            saveGroup(def);
        }
    }

    private void loadUsers(Connection connection) throws SQLException {
        Map<UUID, List<Node>> nodes = loadNodes(connection, "rexranks_user_nodes", "uuid", true);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, username, primary_group, updated_at FROM rexranks_users")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                UserImpl user = context.newUser(uuid, rs.getString("username"), rs.getString("primary_group"));
                List<Node> assigned = nodes.getOrDefault(uuid, List.of());
                user.dataImpl().replaceAll(assigned);
                user.setUpdatedAt(rs.getLong("updated_at"));
                users.put(uuid, user);
                uuidCache.put(user.getUsername().toLowerCase(Locale.ROOT), uuid);
            }
        }
    }

    private void loadGroups(Connection connection) throws SQLException {
        Map<String, List<Node>> nodes = loadNamedNodes(connection, "rexranks_group_nodes");
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name, updated_at FROM rexranks_groups")) {
            while (rs.next()) {
                String name = rs.getString("name");
                GroupImpl group = context.newGroup(name);
                group.dataImpl().replaceAll(nodes.getOrDefault(name.toLowerCase(Locale.ROOT), List.of()));
                group.setUpdatedAt(rs.getLong("updated_at"));
                groups.put(group.getName(), group);
            }
        }
    }

    private void loadTracks(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name, groups_json FROM rexranks_tracks")) {
            while (rs.next()) {
                TrackImpl track = context.newTrack(rs.getString("name"));
                List<String> names = gson.fromJson(rs.getString("groups_json"), GROUP_LIST_TYPE);
                if (names != null) {
                    track.setGroups(names);
                }
                tracks.put(track.getName(), track);
            }
        }
    }

    private void loadUuid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT username, uuid FROM rexranks_uuid_cache")) {
            while (rs.next()) {
                try {
                    uuidCache.put(rs.getString("username").toLowerCase(Locale.ROOT), UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private Map<UUID, List<Node>> loadNodes(Connection connection, String table, String ownerCol, boolean uuid) throws SQLException {
        Map<UUID, List<Node>> map = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT " + ownerCol + ", type, key_name, " + valueSelect() + ", negated, expiry, contexts FROM " + table)) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString(ownerCol));
                map.computeIfAbsent(id, k -> new ArrayList<>()).add(readNode(rs));
            }
        }
        return map;
    }

    private Map<String, List<Node>> loadNamedNodes(Connection connection, String table) throws SQLException {
        Map<String, List<Node>> map = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name, type, key_name, " + valueSelect() + ", negated, expiry, contexts FROM " + table)) {
            while (rs.next()) {
                String name = rs.getString("name").toLowerCase(Locale.ROOT);
                map.computeIfAbsent(name, k -> new ArrayList<>()).add(readNode(rs));
            }
        }
        return map;
    }

    private Node readNode(ResultSet rs) throws SQLException {
        NodeType type = NodeType.valueOf(rs.getString("type").toUpperCase(Locale.ROOT));
        boolean negated = rs.getBoolean("negated") || rs.getInt("negated") != 0;
        return Node.builder(type, rs.getString("key_name"))
                .value(rs.getString("node_value"))
                .negated(negated)
                .expiry(rs.getLong("expiry"))
                .contexts(contextsFromJson(rs.getString("contexts")))
                .build();
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertUser(connection, user);
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM rexranks_user_nodes WHERE uuid = ?")) {
                    delete.setString(1, user.getUniqueId().toString());
                    delete.executeUpdate();
                }
                insertNodes(connection, "rexranks_user_nodes", "uuid", user.getUniqueId().toString(), user.data().toSet());
                upsertUuid(connection, user.getUsername(), user.getUniqueId());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void upsertUser(Connection connection, UserImpl user) throws SQLException {
        String sql = switch (dialect) {
            case MYSQL -> """
                    INSERT INTO rexranks_users (uuid, username, primary_group, updated_at) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE username = VALUES(username), primary_group = VALUES(primary_group), updated_at = VALUES(updated_at)
                    """;
            case SQLITE -> """
                    INSERT INTO rexranks_users (uuid, username, primary_group, updated_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, primary_group = excluded.primary_group, updated_at = excluded.updated_at
                    """;
            case H2 -> """
                    MERGE INTO rexranks_users (uuid, username, primary_group, updated_at) KEY (uuid) VALUES (?, ?, ?, ?)
                    """;
        };
        try (PreparedStatement statement = connection.prepareStatement(sql.stripIndent())) {
            statement.setString(1, user.getUniqueId().toString());
            statement.setString(2, truncate(user.getUsername(), 16));
            statement.setString(3, user.getPrimaryGroup());
            statement.setLong(4, user.getUpdatedAt());
            statement.executeUpdate();
        }
    }

    private void upsertGroupRow(Connection connection, GroupImpl group) throws SQLException {
        String sql = switch (dialect) {
            case MYSQL -> "INSERT INTO rexranks_groups (name, updated_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)";
            case SQLITE -> "INSERT INTO rexranks_groups (name, updated_at) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET updated_at = excluded.updated_at";
            case H2 -> "MERGE INTO rexranks_groups (name, updated_at) KEY (name) VALUES (?, ?)";
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, group.getName());
            statement.setLong(2, group.getUpdatedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized void saveGroup(GroupImpl group) throws Exception {
        groups.put(group.getName(), group);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertGroupRow(connection, group);
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM rexranks_group_nodes WHERE name = ?")) {
                    delete.setString(1, group.getName());
                    delete.executeUpdate();
                }
                insertNodes(connection, "rexranks_group_nodes", "name", group.getName(), group.data().toSet());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public synchronized void saveTrack(TrackImpl track) throws Exception {
        tracks.put(track.getName(), track);
        String sql = switch (dialect) {
            case MYSQL -> "INSERT INTO rexranks_tracks (name, groups_json) VALUES (?, ?) ON DUPLICATE KEY UPDATE groups_json = VALUES(groups_json)";
            case SQLITE -> "INSERT INTO rexranks_tracks (name, groups_json) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET groups_json = excluded.groups_json";
            case H2 -> "MERGE INTO rexranks_tracks (name, groups_json) KEY (name) VALUES (?, ?)";
        };
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.getName());
            statement.setString(2, gson.toJson(track.getGroups()));
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized boolean deleteUser(UUID uniqueId) throws Exception {
        UserImpl removed = users.remove(uniqueId);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement nodes = connection.prepareStatement("DELETE FROM rexranks_user_nodes WHERE uuid = ?")) {
                nodes.setString(1, uniqueId.toString());
                nodes.executeUpdate();
            }
            try (PreparedStatement user = connection.prepareStatement("DELETE FROM rexranks_users WHERE uuid = ?")) {
                user.setString(1, uniqueId.toString());
                user.executeUpdate();
            }
        }
        uuidCache.values().removeIf(uniqueId::equals);
        return removed != null;
    }

    @Override
    public synchronized boolean deleteGroup(String name) throws Exception {
        String id = name.toLowerCase(Locale.ROOT);
        GroupImpl removed = groups.remove(id);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement nodes = connection.prepareStatement("DELETE FROM rexranks_group_nodes WHERE name = ?")) {
                nodes.setString(1, id);
                nodes.executeUpdate();
            }
            try (PreparedStatement group = connection.prepareStatement("DELETE FROM rexranks_groups WHERE name = ?")) {
                group.setString(1, id);
                group.executeUpdate();
            }
        }
        return removed != null;
    }

    @Override
    public synchronized boolean deleteTrack(String name) throws Exception {
        String id = name.toLowerCase(Locale.ROOT);
        TrackImpl removed = tracks.remove(id);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM rexranks_tracks WHERE name = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
        return removed != null;
    }

    @Override
    public Map<String, UUID> uuidCache() {
        return Collections.unmodifiableMap(uuidCache);
    }

    @Override
    public synchronized void saveUuidMapping(String username, UUID uniqueId) throws Exception {
        uuidCache.put(username.toLowerCase(Locale.ROOT), uniqueId);
        try (Connection connection = dataSource.getConnection()) {
            upsertUuid(connection, username, uniqueId);
        }
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

    /**
     * Re-reads rows from the database that changed after {@code since} and merges
     * them into the in-memory cache. Used by cross-server database polling.
     */
    public synchronized int pullRemoteChanges(long since) throws Exception {
        int changes = 0;
        try (Connection connection = dataSource.getConnection()) {
            Map<UUID, List<Node>> userNodes = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, type, key_name, " + valueSelect() + ", negated, expiry, contexts FROM rexranks_user_nodes WHERE uuid IN (SELECT uuid FROM rexranks_users WHERE updated_at > ?)")) {
                statement.setLong(1, since);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        userNodes.computeIfAbsent(UUID.fromString(rs.getString("uuid")), k -> new ArrayList<>()).add(readNode(rs));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, username, primary_group, updated_at FROM rexranks_users WHERE updated_at > ?")) {
                statement.setLong(1, since);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        UserImpl user = context.newUser(uuid, rs.getString("username"), rs.getString("primary_group"));
                        user.dataImpl().replaceAll(userNodes.getOrDefault(uuid, List.of()));
                        user.setUpdatedAt(rs.getLong("updated_at"));
                        users.put(uuid, user);
                        uuidCache.put(user.getUsername().toLowerCase(Locale.ROOT), uuid);
                        changes++;
                    }
                }
            }
            Map<String, List<Node>> groupNodes = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name, type, key_name, " + valueSelect() + ", negated, expiry, contexts FROM rexranks_group_nodes WHERE name IN (SELECT name FROM rexranks_groups WHERE updated_at > ?)")) {
                statement.setLong(1, since);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        groupNodes.computeIfAbsent(rs.getString("name").toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(readNode(rs));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name, updated_at FROM rexranks_groups WHERE updated_at > ?")) {
                statement.setLong(1, since);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        GroupImpl group = context.newGroup(name);
                        group.dataImpl().replaceAll(groupNodes.getOrDefault(name.toLowerCase(Locale.ROOT), List.of()));
                        group.setUpdatedAt(rs.getLong("updated_at"));
                        groups.put(group.getName(), group);
                        changes++;
                    }
                }
            }
            tracks.clear();
            loadTracks(connection);
            loadUuid(connection);
        }
        return changes;
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
        dataSource.close();
    }

    private void insertNodes(Connection connection, String table, String ownerCol, String owner, Collection<Node> nodes) throws SQLException {
        String sql = "INSERT INTO " + table + " (" + ownerCol + ", type, key_name, " + valueIdent() + ", negated, expiry, contexts) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Node node : nodes) {
                statement.setString(1, owner);
                statement.setString(2, node.type().name());
                statement.setString(3, node.key());
                statement.setString(4, node.value());
                statement.setBoolean(5, node.negated());
                statement.setLong(6, node.expiryUnix());
                statement.setString(7, contextsToJson(node.contexts()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertUuid(Connection connection, String username, UUID uuid) throws SQLException {
        String sql = switch (dialect) {
            case MYSQL -> "INSERT INTO rexranks_uuid_cache (username, uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = VALUES(uuid)";
            case SQLITE -> "INSERT INTO rexranks_uuid_cache (username, uuid) VALUES (?, ?) ON CONFLICT(username) DO UPDATE SET uuid = excluded.uuid";
            case H2 -> "MERGE INTO rexranks_uuid_cache (username, uuid) KEY (username) VALUES (?, ?)";
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, truncate(username.toLowerCase(Locale.ROOT), 16));
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    private String contextsToJson(ImmutableContextSet contexts) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Context context : contexts.toSet()) {
            map.computeIfAbsent(context.key(), k -> new ArrayList<>()).add(context.value());
        }
        return gson.toJson(map);
    }

    private ImmutableContextSet contextsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}") || json.equals("null")) {
            return ImmutableContextSet.empty();
        }
        Map<String, List<String>> map = gson.fromJson(json, CONTEXT_TYPE);
        if (map == null || map.isEmpty()) {
            return ImmutableContextSet.empty();
        }
        ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
        map.forEach((k, values) -> {
            if (values != null) {
                values.forEach(v -> builder.add(k, v));
            }
        });
        return builder.build();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
