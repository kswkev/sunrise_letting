package com.sunriseletting.identity.rememberme;

import java.time.Instant;
import java.util.UUID;

import com.sunriseletting.identity.account.Role;

/**
 * The account a remember-me login belongs to and the token that replaces the one just used. Also the body of the
 * response.
 */
public record VerifiedRememberMeToken(UUID accountId, Role role, String token, Instant expiresAt) {

    /** Hides the token, so it can never reach a log. */
    @Override
    public String toString() {
        return "VerifiedRememberMeToken[accountId=" + accountId + ", role=" + role + ", expiresAt=" + expiresAt + "]";
    }
}
