package com.sunriseletting.identity.rememberme;

public record VerifyRememberMeTokenRequest(String series, String token) {

    /** Hides the token, so it can never reach a log. */
    @Override
    public String toString() {
        return "VerifyRememberMeTokenRequest[series=" + series + "]";
    }
}
