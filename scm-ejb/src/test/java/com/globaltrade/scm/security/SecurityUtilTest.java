package com.globaltrade.scm.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container-free unit tests for {@link SecurityUtil}. Deliberately does
 * NOT test {@link SupplyChainLoginModule} here: that class's interesting
 * behavior is its interaction with the JAAS callback handler and a real
 * (or container-provided) {@code DataSource}, which belongs in an
 * in-container integration test, not a plain unit test -- see
 * {@code it/} for that coverage. This class tests only the pure,
 * container-independent cryptographic logic.
 */
class SecurityUtilTest {

    @Test
    void hashedPasswordVerifiesAgainstOriginalPlaintext() {
        String hash = SecurityUtil.hashPassword("Correct-Horse-Battery-Staple-1");
        assertTrue(SecurityUtil.verifyPassword("Correct-Horse-Battery-Staple-1", hash));
    }

    @Test
    void hashedPasswordRejectsWrongPlaintext() {
        String hash = SecurityUtil.hashPassword("Correct-Horse-Battery-Staple-1");
        assertFalse(SecurityUtil.verifyPassword("wrong-password", hash));
    }

    @Test
    void twoHashesOfTheSamePasswordAreNotEqual() {
        // Each call generates a fresh random salt; identical stored hashes
        // for identical passwords would leak which users share a password.
        String first = SecurityUtil.hashPassword("shared-password");
        String second = SecurityUtil.hashPassword("shared-password");
        assertNotEquals(first, second);
        assertTrue(SecurityUtil.verifyPassword("shared-password", first));
        assertTrue(SecurityUtil.verifyPassword("shared-password", second));
    }

    @Test
    void verifyPasswordReturnsFalseRatherThanThrowingForMalformedStoredValue() {
        assertFalse(SecurityUtil.verifyPassword("anything", "not-a-valid-stored-hash"));
        assertFalse(SecurityUtil.verifyPassword("anything", null));
    }

    @Test
    void hashPasswordRejectsNullOrEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> SecurityUtil.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> SecurityUtil.hashPassword(""));
    }

    @Test
    void isValidRoleAcceptsOnlyKnownUserRoleNames() {
        assertTrue(SecurityUtil.isValidRole("ADMIN"));
        assertTrue(SecurityUtil.isValidRole("CUSTOMS_AGENT"));
        assertFalse(SecurityUtil.isValidRole("SUPER_ADMIN"));
        assertFalse(SecurityUtil.isValidRole(null));
    }
}
