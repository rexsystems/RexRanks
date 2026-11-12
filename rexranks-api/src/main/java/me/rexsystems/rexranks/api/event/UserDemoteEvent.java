package me.rexsystems.rexranks.api.event;

import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.api.user.User;

/**
 * Fired before a user is demoted along a track. Cancel to prevent the demotion.
 */
public class UserDemoteEvent implements Cancellable {
    private final User user;
    private final Track track;
    private final String from;
    private final String to;
    private boolean cancelled;

    public UserDemoteEvent(User user, Track track, String from, String to) {
        this.user = user;
        this.track = track;
        this.from = from;
        this.to = to;
    }

    public User getUser() {
        return user;
    }

    public Track getTrack() {
        return track;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
