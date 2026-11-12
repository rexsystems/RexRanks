package me.rexsystems.rexranks.storage;

import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.TrackImpl;
import me.rexsystems.rexranks.model.UserImpl;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface Storage extends AutoCloseable {
    String name();

    void init() throws Exception;

    void loadAll() throws Exception;

    Collection<UserImpl> getLoadedUsers();

    Collection<GroupImpl> getLoadedGroups();

    Collection<TrackImpl> getLoadedTracks();

    Optional<UserImpl> getUser(UUID uniqueId);

    Optional<GroupImpl> getGroup(String name);

    Optional<TrackImpl> getTrack(String name);

    void saveUser(UserImpl user) throws Exception;

    void saveGroup(GroupImpl group) throws Exception;

    void saveTrack(TrackImpl track) throws Exception;

    boolean deleteUser(UUID uniqueId) throws Exception;

    boolean deleteGroup(String name) throws Exception;

    boolean deleteTrack(String name) throws Exception;

    Map<String, UUID> uuidCache();

    void saveUuidMapping(String username, UUID uniqueId) throws Exception;

    Collection<UUID> allUserIds() throws Exception;

    /**
     * Users whose {@code updatedAt} is strictly greater than {@code since}.
     */
    Collection<UserImpl> usersUpdatedSince(long since) throws Exception;

    Collection<GroupImpl> groupsUpdatedSince(long since) throws Exception;

    long maxUpdatedAt() throws Exception;

    @Override
    void close();
}
