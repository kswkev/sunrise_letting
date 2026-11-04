package com.sunriseletting.identity.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountDetailsTest {

    @Test
    void toStringHidesThePasswords() {
        String text = new AccountDetails("Mr", null, "Jo", "Smith", "jo@example.com", "07700 900123",
                "Secret-password-1", "Secret-confirm-1").toString();

        assertThat(text).as("the password should not appear").doesNotContain("Secret-password-1");
        assertThat(text).as("the confirm password should not appear").doesNotContain("Secret-confirm-1");
    }
}
