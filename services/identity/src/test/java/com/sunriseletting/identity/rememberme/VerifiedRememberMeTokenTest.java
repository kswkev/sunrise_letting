package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sunriseletting.identity.account.Role;

class VerifiedRememberMeTokenTest {

    @Test
    void toStringHidesTheToken() {
        String text = new VerifiedRememberMeToken(UUID.randomUUID(), Role.VISITOR, "the-raw-token", Instant.EPOCH).toString();

        assertThat(text).as("the token should not appear").doesNotContain("the-raw-token");
    }
}
