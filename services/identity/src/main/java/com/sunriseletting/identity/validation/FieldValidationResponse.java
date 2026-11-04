package com.sunriseletting.identity.validation;

/**
 * Body of the validate-one-field response.
 *
 * @param error the first failing rule, or null if the value is valid
 */
public record FieldValidationResponse(AccountFieldError error) {
}
