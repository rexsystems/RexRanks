package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.engine.PermissionCalculator;
import me.rexsystems.rexranks.event.SimpleEventBus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackImplTest {
    @Test
    void promoteAndDemoteAlongTrack() {
        PermissionCalculator calc = new PermissionCalculator(n -> Optional.empty(), "default", false);
        UserImpl user = new UserImpl(UUID.randomUUID(), "Steve", "default", calc, QueryOptions::defaultContextual);
        TrackImpl track = new TrackImpl("staff", new SimpleEventBus(Logger.getLogger("test")), true);
        track.setGroups(List.of("helper", "mod", "admin"));

        Track.PromotionResult first = track.promote(user);
        assertTrue(first.success());
        assertEquals("helper", first.to());
        assertEquals("helper", user.getPrimaryGroup());

        Track.PromotionResult second = track.promote(user);
        assertEquals("mod", second.to());

        Track.PromotionResult third = track.promote(user);
        assertEquals("admin", third.to());

        Track.PromotionResult top = track.promote(user);
        assertFalse(top.success());

        Track.PromotionResult down = track.demote(user);
        assertEquals("mod", down.to());
        assertEquals("mod", user.getPrimaryGroup());
    }
}
