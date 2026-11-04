package com.sunriseletting.web.identity;

import java.util.Map;

import com.sunriseletting.web.security.AccountRole;

/** What identity said about an email and password. */
public sealed interface AuthenticationResult {

    record SignedIn(String accountId, AccountRole role, boolean temporaryPassword) implements AuthenticationResult {
    }

    /** Fields left empty: identity's field name to its error code. */
    record FieldErrors(Map<String, String> errors) implements AuthenticationResult {
    }

    /** An unknown email or a wrong password. Identity doesn't say which, and neither must the page. */
    record InvalidCredentials() implements AuthenticationResult {
    }

    /** The request couldn't be completed: a connection error, a timeout, a 5xx or a response outside the contract. */
    record Unavailable() implements AuthenticationResult {
    }

    static AuthenticationResult invalidCredentials() {
        return new InvalidCredentials();
    }

    static AuthenticationResult unavailable() {
        return new Unavailable();
    }
}
