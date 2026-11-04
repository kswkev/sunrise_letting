package com.sunriseletting.identity.validation;

/**
 * The account fields as submitted, before any trimming or normalising. Also the body of the create request.
 */
public record AccountDetails(
        String title,
        String titleOther,
        String firstName,
        String surname,
        String email,
        String phone,
        String password,
        String confirmPassword) {

    /** Hides every value, so the passwords and personal details can never reach a log. */
    @Override
    public String toString() {
        return "AccountDetails[hidden]";
    }
}
