package com.sunriseletting.identity.validation;

import java.util.Locale;

/**
 * The stored form of an email address, used both when creating an account and when looking one up.
 */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
