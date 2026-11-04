package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordRulesTest {

    private final PasswordRules rules = new PasswordRules();

    /** A valid password of the given length: one of each character type, padded with lower-case letters. */
    private static String passwordOfLength(int length) {
        return "Aa1!" + "a".repeat(length - 4);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingPasswordIsRequired(String password) {
        assertEquals(Optional.of(AccountFieldError.PASSWORD_REQUIRED), rules.check(password),
                "a missing password should be reported as required");
    }

    static Stream<Arguments> lengths() {
        return Stream.of(
                Arguments.of(7, Optional.of(AccountFieldError.PASSWORD_TOO_SHORT)),
                Arguments.of(8, Optional.empty()),
                Arguments.of(128, Optional.empty()),
                Arguments.of(129, Optional.of(AccountFieldError.PASSWORD_TOO_LONG)));
    }

    @ParameterizedTest
    @MethodSource("lengths")
    void passwordMustBeEightToOneHundredAndTwentyEightCharacters(int length, Optional<AccountFieldError> expected) {
        assertEquals(expected, rules.check(passwordOfLength(length)),
                "a password of " + length + " characters should give " + expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefg1!", // no upper-case letter
            "ABCDEFG1!", // no lower-case letter
            "Abcdefgh!", // no number
            "Abcdefgh1", // no symbol
            "Abcdefg 1", // whitespace is not a symbol
    })
    void missingCharacterTypeIsReported(String password) {
        assertEquals(Optional.of(AccountFieldError.PASSWORD_MISSING_CHARACTER_TYPES), rules.check(password),
                "'" + password + "' should be reported as missing a character type");
    }

    @Test
    void lengthIsCheckedBeforeCharacterTypes() {
        assertEquals(Optional.of(AccountFieldError.PASSWORD_TOO_SHORT), rules.check("abc"),
                "a short password missing character types should be reported as too short");
        assertEquals(Optional.of(AccountFieldError.PASSWORD_TOO_LONG), rules.check("a".repeat(129)),
                "a long password missing character types should be reported as too long");
    }

    @Test
    void lengthIsCountedInUnicodeCharacters() {
        String sevenCharactersInFourteenUnits = "😀".repeat(7);
        assertEquals(Optional.of(AccountFieldError.PASSWORD_TOO_SHORT), rules.check(sevenCharactersInFourteenUnits),
                "seven emoji are seven characters, even though Java stores them in fourteen units");
    }

    @Test
    void passwordIsNotTrimmed() {
        assertEquals(Optional.of(AccountFieldError.PASSWORD_MISSING_CHARACTER_TYPES), rules.check("        "),
                "eight spaces should count as eight characters rather than as an empty password");
        assertEquals(Optional.empty(), rules.check(" Aa1!aaa"),
                "a leading space should count towards the length of the password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1!", "Abcdefg1_", "Abcdefg1£", "Abcdefg1😀"})
    void anythingThatIsNotALetterDigitOrWhitespaceIsASymbol(String password) {
        assertEquals(Optional.empty(), rules.check(password), "'" + password + "' should count as having a symbol");
    }
}
