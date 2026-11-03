package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class IssuedRememberMeTokenTest {

    @Test
    void toStringHidesTheToken() {
        String text = new IssuedRememberMeToken("the-series", "the-raw-token", Instant.EPOCH).toString();

        assertThat(text).as("the token should not appear").doesNotContain("the-raw-token");
    }
}
