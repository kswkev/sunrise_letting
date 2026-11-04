package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void emailIsTrimmedAndLowerCased() {
        assertEquals("jo@example.com", EmailAddress.normalise("  JO@Example.COM\t"),
                "the email should be trimmed and lower-cased");
    }
}
