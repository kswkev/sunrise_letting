package com.sunriseletting.identity.rememberme;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Makes and hashes the random values of a remember-me login. The values are URL-safe Base64, so they can go in a cookie.
 */
final class RememberMeTokenValues {

    private static final int SERIES_BYTES = 16;
    private static final int TOKEN_BYTES = 32;
    private static final Pattern SERIES = Pattern.compile("[A-Za-z0-9_-]{22}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private RememberMeTokenValues() {
    }

    static String newSeries() {
        return randomValue(SERIES_BYTES);
    }

    static String newToken() {
        return randomValue(TOKEN_BYTES);
    }

    /** Whether this could be a series made here. Anything else can't be stored, so it needn't reach the data store. */
    static boolean isWellFormedSeries(String series) {
        return series != null && SERIES.matcher(series).matches();
    }

    /**
     * SHA-256 rather than BCrypt, because a token is 256 random bits and can't be guessed however fast the hash is,
     * and it is checked on every return to the site.
     */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    private static String randomValue(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
