package com.globaltrade.scm.security;
import com.globaltrade.scm.common.enums.UserRole;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
public final class SecurityUtil {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private SecurityUtil() {
    }
    public static String hashPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(plaintextPassword.toCharArray(), salt, DEFAULT_ITERATIONS);
        return DEFAULT_ITERATIONS + ":" + encode(salt) + ":" + encode(hash);
    }
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
            return false;
        }
    }
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
