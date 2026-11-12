package me.rexsystems.rexranks.api.track;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loads, creates, and saves tracks.
 */
public interface TrackManager {
    CompletableFuture<Track> createAndLoadTrack(String name);

    Optional<Track> getTrack(String name);

    Collection<Track> getLoadedTracks();

    CompletableFuture<Void> saveTrack(Track track);

    CompletableFuture<Boolean> deleteTrack(Track track);

    CompletableFuture<Void> loadAllTracks();
}
