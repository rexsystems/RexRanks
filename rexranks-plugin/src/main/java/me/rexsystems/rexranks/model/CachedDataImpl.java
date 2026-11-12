package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.data.CachedData;
import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.data.CachedPermissionData;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.engine.PermissionCalculator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CachedDataImpl implements CachedData {
    private final Supplier<Set<Node>> nodes;
    private final Supplier<String> primaryGroup;
    private final PermissionCalculator calculator;
    private final Supplier<QueryOptions> defaultOptions;
    private final ConcurrentHashMap<QueryOptions, CachedPermissionData> permissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<QueryOptions, CachedMetaData> meta = new ConcurrentHashMap<>();

    public CachedDataImpl(Supplier<Set<Node>> nodes, Supplier<String> primaryGroup,
                          PermissionCalculator calculator, Supplier<QueryOptions> defaultOptions) {
        this.nodes = nodes;
        this.primaryGroup = primaryGroup;
        this.calculator = calculator;
        this.defaultOptions = defaultOptions;
    }

    @Override
    public CachedPermissionData getPermissionData() {
        return getPermissionData(defaultOptions.get());
    }

    @Override
    public CachedPermissionData getPermissionData(QueryOptions options) {
        return permissions.computeIfAbsent(options, o -> calculator.permissionData(nodes.get(), primaryGroup.get(), o));
    }

    @Override
    public CachedMetaData getMetaData() {
        return getMetaData(defaultOptions.get());
    }

    @Override
    public CachedMetaData getMetaData(QueryOptions options) {
        return meta.computeIfAbsent(options, o -> calculator.metaData(nodes.get(), primaryGroup.get(), o));
    }

    @Override
    public void invalidate() {
        permissions.clear();
        meta.clear();
    }
}
