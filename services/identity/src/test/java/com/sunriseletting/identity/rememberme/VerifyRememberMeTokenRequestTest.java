package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerifyRememberMeTokenRequestTest {

    @Test
    void toStringHidesTheToken() {
        String text = new VerifyRememberMeTokenRequest("the-series", "the-raw-token").toString();

        assertThat(text).as("the token should not appear").doesNotContain("the-raw-token");
    }
}
