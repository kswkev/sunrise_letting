package com.sunriseletting.identity.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldValidationRequestTest {

    @Test
    void toStringHidesTheValues() {
        String text = new FieldValidationRequest("Secret-value-1", "Secret-password-1").toString();

        assertThat(text).as("the value could be a password, so it should not appear").doesNotContain("Secret-value-1");
        assertThat(text).as("the password should not appear").doesNotContain("Secret-password-1");
    }
}
