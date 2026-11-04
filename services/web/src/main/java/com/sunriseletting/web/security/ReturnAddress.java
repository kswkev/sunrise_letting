package com.sunriseletting.web.security;

import java.util.regex.Pattern;

/** The {@code returnTo} rules (design-system.md 3.5): it is followed only if it is a path on this site. */
public final class ReturnAddress {

    /** Control characters and backslashes in percent-encoded form, and an encoded second slash at the start. */
    private static final Pattern ENCODED_UNSAFE = Pattern.compile("%(?:[01][0-9a-f]|7f|5c)|^/%2f", Pattern.CASE_INSENSITIVE);

    private ReturnAddress() {
    }

    /** The value if it is a path on this site. Anything else counts as no return address, so null. */
    public static String validOrNull(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")) {
            return null;
        }
        if (value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return ENCODED_UNSAFE.matcher(value).find() ? null : value;
    }
}
