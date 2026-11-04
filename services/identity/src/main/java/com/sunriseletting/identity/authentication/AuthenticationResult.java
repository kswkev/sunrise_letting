package com.sunriseletting.identity.authentication;

import java.util.Map;
import java.util.UUID;

import com.sunriseletting.identity.account.Role;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;

public sealed interface AuthenticationResult {

    record Authenticated(UUID accountId, Role role, boolean temporaryPassword) implements AuthenticationResult {
    }

    record Invalid(Map<AccountField, AccountFieldError> errors) implements AuthenticationResult {
    }

    /** Covers an unknown email and a wrong password alike, so nothing further up can tell them apart. */
    record InvalidCredentials() implements AuthenticationResult {
    }
}
