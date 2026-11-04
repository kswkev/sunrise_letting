package com.sunriseletting.identity.authentication;

/**
 * The credentials as submitted, before any trimming or normalising. Also the body of the request.
 */
public record AuthenticationRequest(String email, String password) {

    /** Hides every value, so the password can never reach a log. */
    @Override
    public String toString() {
        return "AuthenticationRequest[hidden]";
    }
}
