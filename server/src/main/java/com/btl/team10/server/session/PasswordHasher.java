package com.btl.team10.server.session;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2-HMAC-SHA256 password hashing used by the local SQLite seed and users. */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        requirePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS, KEY_BITS);
        return "pbkdf2_sha256$" + ITERATIONS + '$'
                + Base64.getEncoder().encodeToString(salt) + '$'
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String encodedHash) {
        if (password == null || password.isBlank() || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        if (!encodedHash.startsWith("pbkdf2_sha256$")) {
            // Compatibility for old local demo rows. New rows always use PBKDF2.
            return MessageDigest.isEqual(
                    password.getBytes(StandardCharsets.UTF_8),
                    encodedHash.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = encodedHash.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("PBKDF2 is unavailable", error);
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }
}
