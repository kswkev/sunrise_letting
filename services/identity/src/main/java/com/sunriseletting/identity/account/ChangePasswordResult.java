package com.sunriseletting.identity.account;

import java.util.Map;

import com.sunriseletting.identity.validation.AccountFieldError;

public sealed interface ChangePasswordResult {

    record Changed() implements ChangePasswordResult {
    }

    /**
     * @param errors one code per failing field, under the field's name in the API
     */
    record Invalid(Map<String, AccountFieldError> errors) implements ChangePasswordResult {
    }

    record AccountNotFound() implements ChangePasswordResult {
    }

    record NoTemporaryPassword() implements ChangePasswordResult {
    }
}
