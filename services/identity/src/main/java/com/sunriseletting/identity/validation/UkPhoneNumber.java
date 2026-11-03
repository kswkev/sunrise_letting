package com.sunriseletting.identity.validation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises UK mobile and landline numbers, written with a leading 0 or with +44, and normalises them to E.164.
 */
public final class UkPhoneNumber {

    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("\\+?[0-9 ]+");

    /**
     * After the 0 or +44: mobiles are 07 (except 070 personal numbers and 076 pagers, but including 07624),
     * landlines are 01 or 02. A few 01 numbers have one digit fewer than the rest.
     */
    private static final Pattern UK_NUMBER =
            Pattern.compile("(?:0|\\+44)(7[1-57-9][0-9]{8}|7624[0-9]{6}|1[0-9]{8,9}|2[0-9]{9})");

    private UkPhoneNumber() {
    }

    public static Optional<String> toE164(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String stripped = input.strip();
        if (!ALLOWED_CHARACTERS.matcher(stripped).matches()) {
            return Optional.empty();
        }
        Matcher number = UK_NUMBER.matcher(stripped.replace(" ", ""));
        return number.matches() ? Optional.of("+44" + number.group(1)) : Optional.empty();
    }
}
