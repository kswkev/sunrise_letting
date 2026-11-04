package com.sunriseletting.identity.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PreHashingBCryptPasswordEncoderTest {

    private static final String LONG_ASCII = "Aa1!" + "a".repeat(124);
    private static final String LONG_NON_ASCII = "Aa1!" + "é".repeat(40);

    private final PreHashingBCryptPasswordEncoder encoder = new PreHashingBCryptPasswordEncoder();

    @Test
    void bcryptIsGivenTheBase64OfTheSha256OfThePassword() {
        String base64Sha256OfAbc = "ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=";

        String hash = encoder.encode("abc");

        assertTrue(hash.startsWith("$2"), "the result should be a BCrypt hash");
        assertTrue(new BCryptPasswordEncoder().matches(base64Sha256OfAbc, hash),
                "BCrypt should have hashed the Base64 of the SHA-256 digest");
    }

    @Test
    void shortPasswordMatchesOnlyItself() {
        String hash = encoder.encode("Abcdefg1!");

        assertTrue(encoder.matches("Abcdefg1!", hash), "the original password should match");
        assertFalse(encoder.matches("Abcdefg1?", hash), "a different password should not match");
    }

    static Stream<String> passwordsOverSeventyTwoBytes() {
        return Stream.of(LONG_ASCII, LONG_NON_ASCII);
    }

    @ParameterizedTest
    @MethodSource("passwordsOverSeventyTwoBytes")
    void passwordsOverSeventyTwoBytesAreHashedInFull(String password) {
        String lastCharacterChanged = password.substring(0, password.length() - 1) + "x";
        assertTrue(password.getBytes(StandardCharsets.UTF_8).length > 72, "the test password should be over 72 bytes");

        String hash = encoder.encode(password);

        assertTrue(encoder.matches(password, hash), "the original password should match");
        assertFalse(encoder.matches(lastCharacterChanged, hash), "a password differing only after byte 72 should not match");
    }

    @Test
    void missingPasswordIsNotHashedAndNeverMatches() {
        assertNull(encoder.encode(null), "a missing password should have no hash");
        assertFalse(encoder.matches(null, encoder.encode("Abcdefg1!")), "a missing password should not match");
    }
}
