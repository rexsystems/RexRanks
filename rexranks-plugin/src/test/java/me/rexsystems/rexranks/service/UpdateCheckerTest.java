package me.rexsystems.rexranks.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void comparesSemanticVersions() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"));
        assertFalse(UpdateChecker.isNewer("1.0.0-SNAPSHOT", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0-nightly.12"));
    }
}
