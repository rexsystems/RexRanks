package me.rexsystems.rexranks.api.user;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loads, saves, and looks up users.
 */
public interface UserManager {
    CompletableFuture<User> loadUser(UUID uniqueId);

    CompletableFuture<User> loadUser(UUID uniqueId, String username);

    Optional<User> getUser(UUID uniqueId);

    Optional<User> getUser(String username);

    Collection<User> getLoadedUsers();

    CompletableFuture<Void> saveUser(User user);

    CompletableFuture<SetResult> deleteUser(UUID uniqueId);

    CompletableFuture<Optional<UUID>> lookupUniqueId(String username);

    CompletableFuture<Optional<String>> lookupUsername(UUID uniqueId);

    /**
     * All known user UUIDs (loaded or stored).
     */
    CompletableFuture<Collection<UUID>> getUniqueUsers();

    record SetResult(boolean success, String message) {
        public static SetResult ok() {
            return new SetResult(true, "");
        }

        public static SetResult fail(String message) {
            return new SetResult(false, message);
        }
    }
}
