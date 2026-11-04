package com.sunriseletting.web.identity;

/** The identity service's API, as web uses it. Failures come back as results rather than exceptions. */
public interface IdentityClient {

    /** Checks one field's value against identity's rules. */
    FieldValidationResult validateField(AccountField field, String value);

    /** Checks a value that depends on the password as well, which today means {@link AccountField#CONFIRM_PASSWORD}. */
    FieldValidationResult validateField(AccountField field, String value, String password);

    /** Asks identity to create a Visitor account. */
    CreateVisitorResult createVisitor(CreateVisitorRequest request);

    /** Checks an email and password. Both go to identity exactly as typed. */
    AuthenticationResult authenticate(String email, String password);

    /** Replaces an account's temporary password. Both values go to identity exactly as typed, and identity applies the rules. */
    ChangePasswordResult changePassword(String accountId, String newPassword, String confirmNewPassword);

    /** Asks identity for a remember-me token for an account. */
    RememberMeTokenResult issueRememberMeToken(String accountId);

    /** Exchanges a remember-me token for a new one. Sending a token that was already replaced revokes all of the account's tokens. */
    RememberMeVerifyResult verifyRememberMeToken(String series, String token);

    /** Revokes one series. Nothing comes back: signing out carries on whatever happens here. */
    void revokeRememberMeToken(String series);
}
