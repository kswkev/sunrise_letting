package com.sunriseletting.identity.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticationRequestTest {

    @Test
    void toStringHidesThePassword() {
        String text = new AuthenticationRequest("jo@example.com", "Secret-password-1").toString();

        assertThat(text).as("the password should not appear").doesNotContain("Secret-password-1");
    }
}
