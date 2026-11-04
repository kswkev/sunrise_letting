package com.sunriseletting.identity.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountFieldTest {

    @ParameterizedTest
    @EnumSource(AccountField.class)
    void everyFieldIsFoundByItsApiName(AccountField field) {
        assertEquals(Optional.of(field), AccountField.fromApiName(field.apiName()),
                field + " should be found by its API name");
    }

    @Test
    void unknownApiNameIsNotAField() {
        assertEquals(Optional.empty(), AccountField.fromApiName("TITLE"), "API names should match exactly");
    }
}
