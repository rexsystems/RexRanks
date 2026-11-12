package me.rexsystems.rexranks.api.data;

import me.rexsystems.rexranks.api.query.QueryOptions;

/**
 * Holder for a subject's cached permission and meta results.
 */
public interface CachedData {
    CachedPermissionData getPermissionData();

    CachedPermissionData getPermissionData(QueryOptions options);

    CachedMetaData getMetaData();

    CachedMetaData getMetaData(QueryOptions options);

    void invalidate();
}
