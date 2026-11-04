package com.sunriseletting.web.identity;

/** What identity said about one field's value. */
public sealed interface FieldValidationResult {

    record Valid() implements FieldValidationResult {
    }

    /** The value breaks a rule. The code is one of identity's error codes. */
    record Invalid(String code) implements FieldValidationResult {
    }

    /** The check couldn't be done: a connection error, a timeout, or a response outside the contract. */
    record Unavailable() implements FieldValidationResult {
    }

    static FieldValidationResult valid() {
        return new Valid();
    }

    static FieldValidationResult unavailable() {
        return new Unavailable();
    }
}
