package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UkPhoneNumberTest {

    @ParameterizedTest
    @CsvSource({
            "07700 900123,    +447700900123",
            "+44 7700 900123, +447700900123",
            "+447700900123,   +447700900123",
            "0113 496 0000,   +441134960000",
            "01134960000,     +441134960000",
            "020 7946 0000,   +442079460000",
            "016977 2345,     +44169772345",
            "0300 123 4567,   +443001234567",
            "0333 123 4567,   +443331234567",
            "03451234567,     +443451234567",
            "+44 300 123 4567, +443001234567",
            "'  07700 900123 ', +447700900123",
    })
    void validNumbersAreNormalisedToE164(String input, String expected) {
        assertEquals(Optional.of(expected), UkPhoneNumber.toE164(input),
                "'" + input + "' should normalise to " + expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "0113-496-0000",
            "(0113) 496 0000",
            "07700.900123",
            "44+7700900123",
            "++447700900123",
            "+1 202 555 0100",
            "447700900123",
            "+44 07700 900123",
            "07700 90012",
            "07700 9001234",
            "0113 496",
            "0800 123 4567",
            "0800 123 456",
            "+44 800 123 4567",
            "0300 123 456",
            "0300 123 45678",
            "+44 0300 123 4567",
            "07000 123456",
            "phone",
            "0770O 900123",
            "+",
    })
    void anythingElseIsNotAValidNumber(String input) {
        assertEquals(Optional.empty(), UkPhoneNumber.toE164(input), "'" + input + "' should not be a valid UK number");
    }

    @ParameterizedTest
    @ValueSource(strings = {"07700 900123", "+44 7700 900123", " 0113 496 0000 ", "+1 202 555 0100", "123", "+"})
    void digitsSpacesAndALeadingPlusAreAllowedCharacters(String input) {
        assertTrue(UkPhoneNumber.hasOnlyAllowedCharacters(input), "'" + input + "' should have only allowed characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0113-496-0000", "(0113) 496 0000", "07700.900123", "44+7700900123", "++447700900123",
            "phone", "0770O 900123", "07700\t900123"})
    void anythingElseIsNotAnAllowedCharacter(String input) {
        assertFalse(UkPhoneNumber.hasOnlyAllowedCharacters(input), "'" + input + "' should have a character that is not allowed");
    }
}
