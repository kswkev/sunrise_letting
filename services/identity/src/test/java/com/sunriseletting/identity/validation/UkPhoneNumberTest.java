package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            "07000 123456",
            "phone",
            "0770O 900123",
            "+",
    })
    void anythingElseIsNotAValidNumber(String input) {
        assertEquals(Optional.empty(), UkPhoneNumber.toE164(input), "'" + input + "' should not be a valid UK number");
    }
}
