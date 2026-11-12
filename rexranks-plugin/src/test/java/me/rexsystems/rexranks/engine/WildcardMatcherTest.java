package me.rexsystems.rexranks.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildcardMatcherTest {
    @Test
    void exactBeatsWildcard() {
        int exact = WildcardMatcher.score("essentials.fly", "essentials.fly");
        int wild = WildcardMatcher.score("essentials.*", "essentials.fly");
        int star = WildcardMatcher.score("*", "essentials.fly");
        assertTrue(exact > wild && wild > star);
        assertEquals(-1, WildcardMatcher.score("essentials.fly", "essentials.teleport"));
    }

    @Test
    void wildcardDoesNotMatchParent() {
        assertEquals(-1, WildcardMatcher.score("essentials.*", "essentials"));
        assertTrue(WildcardMatcher.score("essentials.*", "essentials.fly.others") > 0);
    }

    @Test
    void longerWildcardWins() {
        int longer = WildcardMatcher.score("essentials.fly.*", "essentials.fly.others");
        int shorter = WildcardMatcher.score("essentials.*", "essentials.fly.others");
        assertTrue(longer > shorter);
    }

    @Test
    void regexMatch() {
        assertTrue(WildcardMatcher.score("^essentials\\..*$", "essentials.fly", true) > 0);
        assertEquals(-1, WildcardMatcher.score("^worldedit\\..*$", "essentials.fly", true));
    }
}
