package me.rexsystems.rexranks.api.track;

import me.rexsystems.rexranks.api.user.User;

import java.util.List;
import java.util.Optional;

/**
 * An ordered list of groups used for promote / demote.
 */
public interface Track {
    String getName();

    List<String> getGroups();

    void setGroups(List<String> groups);

    void appendGroup(String group);

    void insertGroup(int index, String group);

    boolean removeGroup(String group);

    void clearGroups();

    default int indexOf(String group) {
        return getGroups().indexOf(group.toLowerCase());
    }

    default boolean contains(String group) {
        return indexOf(group) >= 0;
    }

    Optional<String> getNext(String current);

    Optional<String> getPrevious(String current);

    /**
     * Promotes the user along this track. Returns the group they landed on.
     */
    PromotionResult promote(User user);

    PromotionResult demote(User user);

    record PromotionResult(boolean success, String from, String to, String message) {
        public static PromotionResult ok(String from, String to) {
            return new PromotionResult(true, from, to, "");
        }

        public static PromotionResult fail(String message) {
            return new PromotionResult(false, null, null, message);
        }
    }
}
