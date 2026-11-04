package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountCredentialsTest {

    @Test
    void toStringHidesThePasswordHash() {
        String text = new AccountCredentials(UUID.randomUUID(), "the-stored-hash", Role.VISITOR, false).toString();

        assertThat(text).as("the password hash should not appear").doesNotContain("the-stored-hash");
    }
}
