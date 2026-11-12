package me.rexsystems.rexranks.storage;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.engine.PermissionCalculator;
import me.rexsystems.rexranks.event.SimpleEventBus;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
    private StorageContext ctx() {
        PermissionCalculator calc = new PermissionCalculator(n -> Optional.empty(), "default", false);
        return new StorageContext(calc, QueryOptions::defaultContextual, new SimpleEventBus(Logger.getLogger("test")), true, "default");
    }

    @Test
    void yamlRoundTrip(@TempDir Path temp) throws Exception {
        StorageContext context = ctx();
        YamlStorage storage = new YamlStorage(temp, context, Logger.getLogger("test"));
        storage.init();
        storage.loadAll();
        assertTrue(storage.getGroup("default").isPresent());

        UUID id = UUID.randomUUID();
        UserImpl user = context.newUser(id, "Steve", "default");
        user.data().add(Node.permission("essentials.fly"));
        user.data().add(Node.prefix(50, "<#ffaa00>[S] "));
        user.data().add(Node.builder(NodeType.PERMISSION, "essentials.god")
                .value("true")
                .contexts(ImmutableContextSet.of("world", "nether"))
                .expiry(System.currentTimeMillis() + 60_000)
                .build());
        storage.saveUser(user);

        GroupImpl admin = context.newGroup("admin");
        admin.setWeight(100);
        admin.data().add(Node.permission("*"));
        storage.saveGroup(admin);

        TrackImpl track = context.newTrack("staff");
        track.setGroups(List.of("helper", "mod", "admin"));
        storage.saveTrack(track);
        storage.close();

        YamlStorage reload = new YamlStorage(temp, context, Logger.getLogger("test"));
        reload.init();
        reload.loadAll();
        UserImpl loaded = reload.getUser(id).orElseThrow();
        assertEquals("Steve", loaded.getUsername());
        assertEquals(3, loaded.data().size());
        assertEquals(100, reload.getGroup("admin").orElseThrow().getWeight());
        assertEquals(List.of("helper", "mod", "admin"), reload.getTrack("staff").orElseThrow().getGroups());
        reload.close();
    }

    @Test
    void h2RoundTrip() throws Exception {
        StorageContext context = ctx();
        SqlStorage storage = new SqlStorage(SqlDialect.H2, SqlStorage.memoryH2(), context, Logger.getLogger("test"));
        try {
            storage.init();
            storage.loadAll();
            UUID id = UUID.randomUUID();
            UserImpl user = context.newUser(id, "Alex", "admin");
            user.data().add(Node.permission("rexranks.admin"));
            user.data().add(Node.inheritance("admin"));
            storage.saveUser(user);
            GroupImpl admin = context.newGroup("admin");
            admin.setWeight(80);
            admin.data().add(Node.permission("essentials.gamemode"));
            storage.saveGroup(admin);
            assertEquals("admin", storage.getUser(id).orElseThrow().getPrimaryGroup());
            assertEquals(80, storage.getGroup("admin").orElseThrow().getWeight());
            assertEquals(2, storage.getUser(id).orElseThrow().data().size());
        } finally {
            storage.close();
        }
    }
}
