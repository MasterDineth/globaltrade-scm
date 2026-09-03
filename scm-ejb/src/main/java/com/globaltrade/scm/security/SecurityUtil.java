package com.globaltrade.scm.security;

import com.globaltrade.scm.common.enums.UserRole;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Password hashing ({@value #ALGORITHM}, salted, {@value #DEFAULT_ITERATIONS}
 * iterations -- in line with OWASP's minimum guidance for this algorithm)
 * and role-name validation shared by {@link SupplyChainLoginModule} and any
 * administrative user-management path that creates or resets a
 * {@code SystemUser} password.
 *
 * <p>Stored format is {@code iterations:base64(salt):base64(hash)}. Keeping
 * the iteration count embedded in the stored value (rather than fixed
 * application-wide) means {@link #DEFAULT_ITERATIONS} can be raised in a
 * future release without invalidating passwords hashed under the old count:
 * {@link #verifyPassword} always re-derives using whichever count is
 * embedded in the row it is checking against, and a login path can choose to
 * transparently re-hash-and-store on successful verification against a
 * stale (lower) iteration count. That opportunistic upgrade is a policy
 * decision left to the caller (e.g. {@code SupplyChainLoginModule}) rather
 * than baked into this class.</p>
 *
 * <p>{@link #isValidRole(String)} is the single source of truth referenced
 * by {@link UserRole}'s class javadoc: declarative security constraints
 * (web.xml, glassfish-web.xml, {@code @RolesAllowed}) must use role names as
 * static string literals, but any code path that accepts a role name as
 * data (e.g. an admin API that creates a {@code SystemUser}) should still
 * validate it against the enum before persisting.</p>
 */
public final class SecurityUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private SecurityUtil() {
    }

    /**
     * Hashes a plaintext password with a freshly generated random salt.
     * The caller's {@code char[]} (if it has one) is not touched here;
     * {@code plaintextPassword} is a {@code String} because password
     * material at this call site typically originates from form/DTO
     * binding rather than a JAAS {@code PasswordCallback}. Where a
     * {@code char[]} is available (see {@code SupplyChainLoginModule}),
     * prefer clearing it explicitly after use.
     */
    public static String hashPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(plaintextPassword.toCharArray(), salt, DEFAULT_ITERATIONS);
        return DEFAULT_ITERATIONS + ":" + encode(salt) + ":" + encode(hash);
    }

    /**
     * Verifies a submitted plaintext password against a stored
     * {@code iterations:salt:hash} value using a constant-time comparison
     * of the derived hashes, so that authentication failures do not leak
     * timing information about how much of the hash matched.
     */
    public static boolean verifyPassword(String plaintextPassword, String storedValue) {
        if (plaintextPassword == null || storedValue == null) {
            return false;
        }
        String[] parts = storedValue.split(":");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = decode(parts[1]);
            byte[] expectedHash = decode(parts[2]);
            byte[] actualHash = pbkdf2(plaintextPassword.toCharArray(), salt, iterations);
            return constantTimeEquals(expectedHash, actualHash);
        } catch (IllegalArgumentException malformed) {
            // Malformed stored value (bad data, truncated column, etc.) is
            // treated as "does not match" rather than propagated -- a
            // corrupt hash must never be indistinguishable from a system
            // error that a retry might fix.
            return false;
        }
    }

    /**
     * True if {@code roleName} matches exactly one {@link UserRole}
     * constant. Used to validate role data before it is persisted or
     * before it is trusted as an argument to role-mapping configuration.
     */
    public static boolean isValidRole(String roleName) {
        if (roleName == null) {
            return false;
        }
        for (UserRole role : UserRole.values()) {
            if (role.name().equals(roleName)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // PBKDF2WithHmacSHA256 is a mandatory algorithm on every
            // standard JDK security provider; reaching this branch means
            // the JVM itself is misconfigured, which is a system failure,
            // not a business condition -- hence unchecked.
            throw new IllegalStateException("Password hashing algorithm unavailable: " + ALGORITHM, e);
        } finally {
            Arrays.fill(password, ' ');
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
