package com.sunriseletting.web.identity;

/** The identity service's API, as web uses it. Failures come back as results rather than exceptions. */
public interface IdentityClient {

    /** Checks one field's value against identity's rules. */
    FieldValidationResult validateField(AccountField field, String value);

    /** Checks a value that depends on the password as well, which today means {@link AccountField#CONFIRM_PASSWORD}. */
    FieldValidationResult validateField(AccountField field, String value, String password);

    /** Asks identity to create a Visitor account. */
    CreateVisitorResult createVisitor(CreateVisitorRequest request);
}
