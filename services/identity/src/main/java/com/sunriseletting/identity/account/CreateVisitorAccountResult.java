package com.sunriseletting.identity.account;

import java.util.Map;
import java.util.UUID;

import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;

public sealed interface CreateVisitorAccountResult {

    record Created(UUID accountId, Role role) implements CreateVisitorAccountResult {
    }

    record Invalid(Map<AccountField, AccountFieldError> errors) implements CreateVisitorAccountResult {
    }

    record EmailInUse() implements CreateVisitorAccountResult {
    }
}
