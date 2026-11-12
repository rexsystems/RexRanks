package me.rexsystems.rexranks.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the running {@link RexRanks} instance so other plugins can obtain the API
 * without depending on the plugin module.
 *
 * <pre>{@code
 * RexRanks api = RexRanksProvider.get();
 * User user = api.getUserManager().getUser(player.getUniqueId()).orElseThrow();
 * }</pre>
 */
public final class RexRanksProvider {
    private static final AtomicReference<RexRanks> INSTANCE = new AtomicReference<>();

    private RexRanksProvider() {
    }

    public static RexRanks get() {
        RexRanks api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException("RexRanks is not loaded");
        }
        return api;
    }

    public static Optional<RexRanks> getOptional() {
        return Optional.ofNullable(INSTANCE.get());
    }

    public static boolean isLoaded() {
        return INSTANCE.get() != null;
    }

    /**
     * Called by the plugin on enable / disable. Other plugins should not call this.
     */
    public static void register(RexRanks api) {
        INSTANCE.set(api);
    }

    /**
     * Called by the plugin on disable. Other plugins should not call this.
     */
    public static void unregister() {
        INSTANCE.set(null);
    }
}
