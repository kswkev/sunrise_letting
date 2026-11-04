package com.sunriseletting.web.identity;

import java.util.Map;

/** What identity said about replacing a temporary password. */
public sealed interface ChangePasswordResult {

    record Changed() implements ChangePasswordResult {
    }

    /** The fields to correct: identity's field name to its error code. Nothing was changed. */
    record FieldErrors(Map<String, String> errors) implements ChangePasswordResult {
    }

    /** The account's password is already permanent, for example it was changed in another browser. Nothing was changed by this request. */
    record NoTemporaryPassword() implements ChangePasswordResult {
    }

    /** The request couldn't be completed: a connection error, a timeout, a 5xx, an unknown account or a response outside the contract. */
    record Unavailable() implements ChangePasswordResult {
    }

    static ChangePasswordResult changed() {
        return new Changed();
    }

    static ChangePasswordResult noTemporaryPassword() {
        return new NoTemporaryPassword();
    }

    static ChangePasswordResult unavailable() {
        return new Unavailable();
    }
}
