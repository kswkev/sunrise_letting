package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChangePasswordRequestTest {

    @Test
    void toStringHidesThePasswords() {
        String text = new ChangePasswordRequest("New-secret-1", "Confirm-secret-1").toString();

        assertThat(text).as("the new password should not appear").doesNotContain("New-secret-1");
        assertThat(text).as("the confirmation should not appear").doesNotContain("Confirm-secret-1");
    }
}
