package com.sunriseletting.identity.validation;

/**
 * Body of the validate-one-field request.
 *
 * @param value    the value to check, which may itself be a password
 * @param password the password to compare with, only for confirmPassword
 */
public record FieldValidationRequest(String value, String password) {

    /** Hides both values, so the request can never reach a log. */
    @Override
    public String toString() {
        return "FieldValidationRequest[hidden]";
    }
}
