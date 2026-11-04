package com.sunriseletting.identity.rememberme;

import java.time.Instant;

/**
 * A new remember-me login. Also the body of the response. This is the only time the first token is ever available.
 */
public record IssuedRememberMeToken(String series, String token, Instant expiresAt) {

    /** Hides the token, so it can never reach a log. */
    @Override
    public String toString() {
        return "IssuedRememberMeToken[series=" + series + ", expiresAt=" + expiresAt + "]";
    }
}
