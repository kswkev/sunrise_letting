package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RememberMeTokenValuesTest {

    @Test
    void tokenHas256RandomBitsInACookieSafeForm() {
        String token = RememberMeTokenValues.newToken();

        assertThat(token).as("32 bytes of unpadded URL-safe Base64 should be 43 characters").matches("[A-Za-z0-9_-]{43}");
        assertNotEquals(token, RememberMeTokenValues.newToken(), "each token should be different");
    }

    @Test
    void seriesHas128RandomBitsInACookieSafeForm() {
        String series = RememberMeTokenValues.newSeries();

        assertThat(series).as("16 bytes of unpadded URL-safe Base64 should be 22 characters").matches("[A-Za-z0-9_-]{22}");
        assertNotEquals(series, RememberMeTokenValues.newSeries(), "each series should be different");
    }

    @Test
    void newSeriesIsWellFormed() {
        assertTrue(RememberMeTokenValues.isWellFormedSeries(RememberMeTokenValues.newSeries()),
                "a series this service made should be well formed");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "aaaaaaaaaaaaaaaaaaaaa\u0000", "aaaaaaaaaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaaa="})
    void anythingElseIsNotAWellFormedSeries(String series) {
        assertFalse(RememberMeTokenValues.isWellFormedSeries(series), "this service never makes a series like this");
    }

    @Test
    void hashIsTheHexOfTheSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RememberMeTokenValues.hash("abc"),
                "the hash should be the lower-case hex of the SHA-256 digest");
    }
}
