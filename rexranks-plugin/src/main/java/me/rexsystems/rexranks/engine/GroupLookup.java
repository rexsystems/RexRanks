package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.group.Group;

import java.util.Collection;
import java.util.Optional;

/**
 * Resolves a group by name during permission calculation. Implemented by the
 * plugin's group manager; tests supply a simple in-memory map.
 */
@FunctionalInterface
public interface GroupLookup {
    Optional<Group> get(String name);

    static GroupLookup of(Collection<? extends Group> groups) {
        return name -> groups.stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .map(g -> (Group) g)
                .findFirst();
    }
}
