package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class NewAccountTest {

    @Test
    void toStringHidesEveryValue() {
        UUID id = UUID.randomUUID();

        String text = new NewAccount(id, "jo@example.com", "Other", "Reverend", "Joanna", "Smithson", "+447700900123",
                "$2a$10$hash-of-the-password", Role.VISITOR, true).toString();

        assertThat(text).as("the id should not appear").doesNotContain(id.toString());
        assertThat(text).as("the email should not appear").doesNotContain("jo@example.com");
        assertThat(text).as("the title should not appear").doesNotContain("Other");
        assertThat(text).as("the other title should not appear").doesNotContain("Reverend");
        assertThat(text).as("the first name should not appear").doesNotContain("Joanna");
        assertThat(text).as("the surname should not appear").doesNotContain("Smithson");
        assertThat(text).as("the phone should not appear").doesNotContain("+447700900123");
        assertThat(text).as("the password hash should not appear").doesNotContain("$2a$10$hash-of-the-password");
        assertThat(text).as("the text should say the values are hidden").isEqualTo("NewAccount[hidden]");
    }
}
