package me.rexsystems.rexranks.manager;

import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.api.track.TrackManager;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.storage.Storage;
import me.rexsystems.rexranks.storage.StorageContext;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TrackManagerImpl implements TrackManager {
    private final Storage storage;
    private final StorageContext context;
    private final Executor async;

    public TrackManagerImpl(Storage storage, StorageContext context, Executor async) {
        this.storage = storage;
        this.context = context;
        this.async = async;
    }

    @Override
    public CompletableFuture<Track> createAndLoadTrack(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String id = name.toLowerCase(Locale.ROOT);
            Optional<TrackImpl> existing = storage.getTrack(id);
            if (existing.isPresent()) {
                return existing.get();
            }
            TrackImpl track = context.newTrack(id);
            try {
                storage.saveTrack(track);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create track " + id, e);
            }
            return track;
        }, async);
    }

    @Override
    public Optional<Track> getTrack(String name) {
        return storage.getTrack(name).map(t -> t);
    }

    @Override
    public Collection<Track> getLoadedTracks() {
        return storage.getLoadedTracks().stream().map(t -> (Track) t).toList();
    }

    @Override
    public CompletableFuture<Void> saveTrack(Track track) {
        return CompletableFuture.runAsync(() -> {
            try {
                storage.saveTrack((TrackImpl) track);
            } catch (Exception e) {
                throw new RuntimeException("Failed to save track " + track.getName(), e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<Boolean> deleteTrack(Track track) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return storage.deleteTrack(track.getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<Void> loadAllTracks() {
        return CompletableFuture.completedFuture(null);
    }
}
