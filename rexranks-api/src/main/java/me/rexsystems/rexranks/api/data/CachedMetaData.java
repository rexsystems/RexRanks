package me.rexsystems.rexranks.api.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolved prefix, suffix, and meta for a subject under a specific query.
 */
public interface CachedMetaData {
    String getPrefix();

    String getSuffix();

    /**
     * All prefixes in priority order (highest first), as raw MiniMessage/legacy strings.
     */
    List<String> getPrefixes();

    List<String> getSuffixes();

    Optional<String> getMeta(String key);

    Map<String, List<String>> getMetaMap();

    int getWeight();

    String getPrimaryGroup();

    String getDisplayName();
}
