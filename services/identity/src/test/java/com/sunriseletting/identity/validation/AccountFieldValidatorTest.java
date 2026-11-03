package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountFieldValidatorTest {

    private final AccountFieldValidator validator = new AccountFieldValidator(new PasswordRules());

    private static final String VALID_PASSWORD = "Abcdefg1!";

    static Stream<Arguments> singleFieldRules() {
        return Stream.of(
                Arguments.of(AccountField.TITLE, null, AccountFieldError.TITLE_REQUIRED),
                Arguments.of(AccountField.TITLE, "", AccountFieldError.TITLE_REQUIRED),
                Arguments.of(AccountField.TITLE, "   ", AccountFieldError.TITLE_REQUIRED),
                Arguments.of(AccountField.TITLE, "Sir", AccountFieldError.TITLE_INVALID),
                Arguments.of(AccountField.TITLE, "mr", AccountFieldError.TITLE_INVALID),

                Arguments.of(AccountField.TITLE_OTHER, "x".repeat(21), AccountFieldError.TITLE_OTHER_TOO_LONG),

                Arguments.of(AccountField.FIRST_NAME, null, AccountFieldError.FIRST_NAME_REQUIRED),
                Arguments.of(AccountField.FIRST_NAME, "  ", AccountFieldError.FIRST_NAME_REQUIRED),
                Arguments.of(AccountField.FIRST_NAME, "x".repeat(101), AccountFieldError.FIRST_NAME_TOO_LONG),

                Arguments.of(AccountField.SURNAME, null, AccountFieldError.SURNAME_REQUIRED),
                Arguments.of(AccountField.SURNAME, "  ", AccountFieldError.SURNAME_REQUIRED),
                Arguments.of(AccountField.SURNAME, "x".repeat(101), AccountFieldError.SURNAME_TOO_LONG),

                Arguments.of(AccountField.EMAIL, null, AccountFieldError.EMAIL_REQUIRED),
                Arguments.of(AccountField.EMAIL, "   ", AccountFieldError.EMAIL_REQUIRED),
                Arguments.of(AccountField.EMAIL, "a".repeat(300), AccountFieldError.EMAIL_TOO_LONG),
                Arguments.of(AccountField.EMAIL, "a".repeat(243) + "@example.com", AccountFieldError.EMAIL_TOO_LONG),
                Arguments.of(AccountField.EMAIL, "no-at-sign.example.com", AccountFieldError.EMAIL_INVALID),
                Arguments.of(AccountField.EMAIL, "two@at@example.com", AccountFieldError.EMAIL_INVALID),
                Arguments.of(AccountField.EMAIL, "@example.com", AccountFieldError.EMAIL_INVALID),
                Arguments.of(AccountField.EMAIL, "jo@localhost", AccountFieldError.EMAIL_INVALID),
                Arguments.of(AccountField.EMAIL, "jo smith@example.com", AccountFieldError.EMAIL_INVALID),

                Arguments.of(AccountField.PHONE, null, AccountFieldError.PHONE_REQUIRED),
                Arguments.of(AccountField.PHONE, "   ", AccountFieldError.PHONE_REQUIRED),
                Arguments.of(AccountField.PHONE, "0113-496-0000", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "(0113) 496 0000", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "07700.900123", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "44+7700900123", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "++447700900123", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "+1 202 555 0100", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "07700 90012", AccountFieldError.PHONE_INVALID),
                Arguments.of(AccountField.PHONE, "seven", AccountFieldError.PHONE_INVALID),

                Arguments.of(AccountField.PASSWORD, null, AccountFieldError.PASSWORD_REQUIRED),
                Arguments.of(AccountField.PASSWORD, "Aa1!", AccountFieldError.PASSWORD_TOO_SHORT));
    }

    @ParameterizedTest
    @MethodSource("singleFieldRules")
    void singleFieldGivesItsFirstFailingRule(AccountField field, String value, AccountFieldError expected) {
        assertEquals(Optional.of(expected), validator.validate(field, value, null),
                field + " with value '" + value + "' should give " + expected);
    }

    static Stream<Arguments> validValues() {
        return Stream.of(
                Arguments.of(AccountField.TITLE, "Mr"),
                Arguments.of(AccountField.TITLE, "Mrs"),
                Arguments.of(AccountField.TITLE, "Miss"),
                Arguments.of(AccountField.TITLE, "Ms"),
                Arguments.of(AccountField.TITLE, "Mx"),
                Arguments.of(AccountField.TITLE, "Dr"),
                Arguments.of(AccountField.TITLE, "Other"),
                Arguments.of(AccountField.TITLE, " Mr "),
                Arguments.of(AccountField.TITLE_OTHER, "x".repeat(20)),
                Arguments.of(AccountField.TITLE_OTHER, " " + "x".repeat(20) + " "),
                Arguments.of(AccountField.FIRST_NAME, "x".repeat(100)),
                Arguments.of(AccountField.FIRST_NAME, " " + "x".repeat(100) + " "),
                Arguments.of(AccountField.SURNAME, "x".repeat(100)),
                Arguments.of(AccountField.EMAIL, "jo@example.com"),
                Arguments.of(AccountField.EMAIL, " Jo@Example.com "),
                Arguments.of(AccountField.EMAIL, "a".repeat(242) + "@example.com"),
                Arguments.of(AccountField.PHONE, "07700 900123"),
                Arguments.of(AccountField.PHONE, "+44 7700 900123"),
                Arguments.of(AccountField.PHONE, "0113 496 0000"),
                Arguments.of(AccountField.PHONE, "01134960000"),
                Arguments.of(AccountField.PHONE, "+447700900123"),
                Arguments.of(AccountField.PASSWORD, VALID_PASSWORD));
    }

    @ParameterizedTest
    @MethodSource("validValues")
    void validValueHasNoError(AccountField field, String value) {
        assertEquals(Optional.empty(), validator.validate(field, value, null),
                field + " with value '" + value + "' should be valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void singleFieldCheckOfOtherTitleOnlyChecksLength(String value) {
        assertEquals(Optional.empty(), validator.validate(AccountField.TITLE_OTHER, value, null),
                "whether Other title is needed depends on Title, so its own check should only be for length");
    }

    @Test
    void lengthIsCountedInUnicodeCharacters() {
        String twentyEmoji = "😀".repeat(20);
        assertEquals(Optional.empty(), validator.validate(AccountField.TITLE_OTHER, twentyEmoji, null),
                "twenty emoji are twenty characters, even though Java stores them in forty units");
    }

    @Test
    void confirmPasswordIsRequired() {
        assertEquals(Optional.of(AccountFieldError.CONFIRM_PASSWORD_REQUIRED),
                validator.validate(AccountField.CONFIRM_PASSWORD, "", VALID_PASSWORD),
                "an empty confirm password should be reported as required");
        assertEquals(Optional.of(AccountFieldError.CONFIRM_PASSWORD_REQUIRED),
                validator.validate(AccountField.CONFIRM_PASSWORD, null, VALID_PASSWORD),
                "a missing confirm password should be reported as required");
    }

    @Test
    void confirmPasswordMustMatchExactly() {
        assertEquals(Optional.empty(), validator.validate(AccountField.CONFIRM_PASSWORD, VALID_PASSWORD, VALID_PASSWORD),
                "matching passwords should be valid");
        assertEquals(Optional.of(AccountFieldError.PASSWORDS_DO_NOT_MATCH),
                validator.validate(AccountField.CONFIRM_PASSWORD, VALID_PASSWORD + " ", VALID_PASSWORD),
                "passwords are not trimmed, so a trailing space should not match");
        assertEquals(Optional.of(AccountFieldError.PASSWORDS_DO_NOT_MATCH),
                validator.validate(AccountField.CONFIRM_PASSWORD, VALID_PASSWORD, null),
                "a confirm password with no password to compare should not match");
    }

    @ParameterizedTest
    @EnumSource(AccountField.class)
    void everyFieldHasAnApiName(AccountField field) {
        assertEquals(Optional.of(field), AccountField.fromApiName(field.apiName()),
                field + " should be found by its API name");
    }

    @Test
    void unknownApiNameIsNotAField() {
        assertEquals(Optional.empty(), AccountField.fromApiName("TITLE"), "API names should match exactly");
    }
}
