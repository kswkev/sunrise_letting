package com.sunriseletting.identity.account;

/**
 * The fields as submitted, never trimmed. Also the body of the request.
 */
public record ChangePasswordRequest(String newPassword, String confirmNewPassword) {

    /** Hides every value, so a password can never reach a log. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[hidden]";
    }
}
