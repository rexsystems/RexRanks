package me.rexsystems.rexranks.manager;

import me.rexsystems.rexranks.api.user.User;
import me.rexsystems.rexranks.api.user.UserManager;
import me.rexsystems.rexranks.model.UserImpl;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserManagerImpl implements UserManager {
    private static final Pattern MOJANG_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern MOJANG_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private final Storage storage;
    private final StorageContext context;
    private final Executor async;
    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentHashMap<String, UUID> lookups = new ConcurrentHashMap<>();

    public UserManagerImpl(Storage storage, StorageContext context, Executor async, Logger logger) {
        this.storage = storage;
        this.context = context;
        this.async = async;
        this.logger = logger;
        storage.uuidCache().forEach((k, v) -> lookups.put(k.toLowerCase(Locale.ROOT), v));
    }

    @Override
    public CompletableFuture<User> loadUser(UUID uniqueId) {
        return loadUser(uniqueId, null);
    }

    @Override
    public CompletableFuture<User> loadUser(UUID uniqueId, String username) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UserImpl> existing = storage.getUser(uniqueId);
            if (existing.isPresent()) {
                UserImpl user = existing.get();
                if (username != null && !username.isBlank()) {
                    user.setUsername(username);
                }
                return user;
            }
            UserImpl created = context.newUser(uniqueId, username == null ? uniqueId.toString() : username, context.defaultGroup());
            try {
                storage.saveUser(created);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save new user " + uniqueId, e);
            }
            lookups.put(created.getUsername().toLowerCase(Locale.ROOT), uniqueId);
            return created;
        }, async);
    }

    @Override
    public Optional<User> getUser(UUID uniqueId) {
        return storage.getUser(uniqueId).map(u -> u);
    }

    @Override
    public Optional<User> getUser(String username) {
        UUID uuid = lookups.get(username.toLowerCase(Locale.ROOT));
        if (uuid == null) {
            uuid = storage.uuidCache().get(username.toLowerCase(Locale.ROOT));
        }
        if (uuid == null) {
            return storage.getLoadedUsers().stream()
                    .filter(u -> u.getUsername().equalsIgnoreCase(username))
                    .map(u -> (User) u)
                    .findFirst();
        }
        return getUser(uuid);
    }

    @Override
    public Collection<User> getLoadedUsers() {
        return storage.getLoadedUsers().stream().map(u -> (User) u).toList();
    }

    @Override
    public CompletableFuture<Void> saveUser(User user) {
        return CompletableFuture.runAsync(() -> {
            try {
                UserImpl impl = (UserImpl) user;
                impl.touch();
                storage.saveUser(impl);
                lookups.put(impl.getUsername().toLowerCase(Locale.ROOT), impl.getUniqueId());
            } catch (Exception e) {
                throw new RuntimeException("Failed to save user " + user.getUniqueId(), e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<SetResult> deleteUser(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean deleted = storage.deleteUser(uniqueId);
                lookups.values().removeIf(uniqueId::equals);
                return deleted ? SetResult.ok() : SetResult.fail("User not found");
            } catch (Exception e) {
                return SetResult.fail(e.getMessage());
            }
        }, async);
    }

    @Override
    public CompletableFuture<Optional<UUID>> lookupUniqueId(String username) {
        return CompletableFuture.supplyAsync(() -> lookupUniqueIdSync(username), async);
    }

    public Optional<UUID> lookupUniqueIdSync(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        UUID cached = lookups.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        cached = storage.uuidCache().get(key);
        if (cached != null) {
            lookups.put(key, cached);
            return Optional.of(cached);
        }
        Optional<User> loaded = getUser(username);
        if (loaded.isPresent()) {
            return Optional.of(loaded.get().getUniqueId());
        }
        Optional<UUID> mojang = fetchMojangUuid(username);
        if (mojang.isPresent()) {
            lookups.put(key, mojang.get());
            try {
                storage.saveUuidMapping(username, mojang.get());
            } catch (Exception ignored) {
            }
            return mojang;
        }
        // Offline-mode fallback is handled by the caller (plugin) which knows the server mode.
        return Optional.empty();
    }

    public UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Optional<UUID> fetchMojangUuid(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            Matcher matcher = MOJANG_ID.matcher(response.body());
            if (matcher.find()) {
                String raw = matcher.group(1);
                String dashed = raw.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                        "$1-$2-$3-$4-$5");
                return Optional.of(UUID.fromString(dashed));
            }
        } catch (IOException | InterruptedException e) {
            logger.fine("Mojang UUID lookup failed for " + username + ": " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.fine("Mojang UUID lookup failed for " + username + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Optional<String>> lookupUsername(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UserImpl> user = storage.getUser(uniqueId);
            if (user.isPresent()) {
                return Optional.of(user.get().getUsername());
            }
            return storage.uuidCache().entrySet().stream()
                    .filter(e -> e.getValue().equals(uniqueId))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }, async);
    }

    @Override
    public CompletableFuture<Collection<UUID>> getUniqueUsers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return storage.allUserIds();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    public void remember(String username, UUID uuid) {
        lookups.put(username.toLowerCase(Locale.ROOT), uuid);
        try {
            storage.saveUuidMapping(username, uuid);
        } catch (Exception ignored) {
        }
    }
}
