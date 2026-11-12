package me.rexsystems.rexranks.model;

import me.rexsystems.rexranks.api.event.EventBus;
import me.rexsystems.rexranks.api.event.UserDemoteEvent;
import me.rexsystems.rexranks.api.event.UserGroupChangeEvent;
import me.rexsystems.rexranks.api.event.UserPromoteEvent;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import me.rexsystems.rexranks.api.track.Track;
import me.rexsystems.rexranks.api.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TrackImpl implements Track {
    private final String name;
    private final CopyOnWriteArrayList<String> groups = new CopyOnWriteArrayList<>();
    private final EventBus eventBus;
    private final boolean updatePrimaryGroup;

    public TrackImpl(String name, EventBus eventBus, boolean updatePrimaryGroup) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.eventBus = eventBus;
        this.updatePrimaryGroup = updatePrimaryGroup;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getGroups() {
        return List.copyOf(groups);
    }

    @Override
    public void setGroups(List<String> groups) {
        this.groups.clear();
        for (String group : groups) {
            appendGroup(group);
        }
    }

    @Override
    public void appendGroup(String group) {
        String id = group.toLowerCase(Locale.ROOT);
        groups.remove(id);
        groups.add(id);
    }

    @Override
    public void insertGroup(int index, String group) {
        String id = group.toLowerCase(Locale.ROOT);
        groups.remove(id);
        int clamped = Math.max(0, Math.min(index, groups.size()));
        groups.add(clamped, id);
    }

    @Override
    public boolean removeGroup(String group) {
        return groups.remove(group.toLowerCase(Locale.ROOT));
    }

    @Override
    public void clearGroups() {
        groups.clear();
    }

    @Override
    public Optional<String> getNext(String current) {
        int index = indexOf(current);
        if (index < 0 || index >= groups.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(groups.get(index + 1));
    }

    @Override
    public Optional<String> getPrevious(String current) {
        int index = indexOf(current);
        if (index <= 0) {
            return Optional.empty();
        }
        return Optional.of(groups.get(index - 1));
    }

    @Override
    public PromotionResult promote(User user) {
        if (groups.isEmpty()) {
            return PromotionResult.fail("Track '" + name + "' is empty");
        }
        int current = currentIndex(user);
        if (current >= groups.size() - 1 && current != -1) {
            return PromotionResult.fail(user.getUsername() + " is already at the top of track '" + name + "'");
        }
        String from = current < 0 ? null : groups.get(current);
        String to = groups.get(current < 0 ? 0 : current + 1);
        UserPromoteEvent event = new UserPromoteEvent(user, this, from, to);
        if (eventBus != null) {
            eventBus.post(event);
            if (event.isCancelled()) {
                return PromotionResult.fail("Promotion was cancelled");
            }
        }
        applyMove(user, from, to);
        return PromotionResult.ok(from, to);
    }

    @Override
    public PromotionResult demote(User user) {
        if (groups.isEmpty()) {
            return PromotionResult.fail("Track '" + name + "' is empty");
        }
        int current = currentIndex(user);
        if (current < 0) {
            return PromotionResult.fail(user.getUsername() + " is not on track '" + name + "'");
        }
        if (current == 0) {
            return PromotionResult.fail(user.getUsername() + " is already at the bottom of track '" + name + "'");
        }
        String from = groups.get(current);
        String to = groups.get(current - 1);
        UserDemoteEvent event = new UserDemoteEvent(user, this, from, to);
        if (eventBus != null) {
            eventBus.post(event);
            if (event.isCancelled()) {
                return PromotionResult.fail("Demotion was cancelled");
            }
        }
        applyMove(user, from, to);
        return PromotionResult.ok(from, to);
    }

    private void applyMove(User user, String from, String to) {
        if (from != null) {
            user.data().removeIf(n -> n.type() == NodeType.INHERITANCE && n.groupName().equalsIgnoreCase(from) && n.contexts().isEmpty() && !n.hasExpiry());
            if (eventBus != null) {
                eventBus.post(new UserGroupChangeEvent(user, UserGroupChangeEvent.Action.PARENT_REMOVE, from, user.getPrimaryGroup()));
            }
        }
        user.data().addOrReplace(Node.inheritance(to));
        if (eventBus != null) {
            eventBus.post(new UserGroupChangeEvent(user, UserGroupChangeEvent.Action.PARENT_ADD, to, user.getPrimaryGroup()));
        }
        if (updatePrimaryGroup) {
            String previous = user.getPrimaryGroup();
            user.setPrimaryGroup(to);
            if (eventBus != null && !previous.equalsIgnoreCase(to)) {
                eventBus.post(new UserGroupChangeEvent(user, UserGroupChangeEvent.Action.PRIMARY_CHANGE, to, previous));
            }
        }
        user.invalidateCache();
    }

    private int currentIndex(User user) {
        int best = -1;
        List<String> owned = new ArrayList<>();
        if (user.getPrimaryGroup() != null) {
            owned.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
        }
        user.data().ofType(NodeType.INHERITANCE).forEach(n -> {
            if (!n.expired() && n.contexts().isEmpty()) {
                owned.add(n.groupName());
            }
        });
        for (int i = 0; i < groups.size(); i++) {
            if (owned.contains(groups.get(i))) {
                best = i;
            }
        }
        return best;
    }
}
