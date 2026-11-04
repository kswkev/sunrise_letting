package com.sunriseletting.web.identity;

import java.time.Instant;

import com.sunriseletting.web.security.AccountRole;

/** What identity said about a remember-me token. A token that was accepted has been replaced, and no longer works. */
public sealed interface RememberMeVerifyResult {

    /** {@link #toString()} leaves out the new token. */
    record Verified(String accountId, AccountRole role, String token, Instant expiresAt) implements RememberMeVerifyResult {

        @Override
        public String toString() {
            return "Verified[redacted]";
        }
    }

    record Invalid() implements RememberMeVerifyResult {
    }

    /** The request couldn't be completed: a connection error, a timeout, a 5xx or a response outside the contract. */
    record Unavailable() implements RememberMeVerifyResult {
    }

    static RememberMeVerifyResult invalid() {
        return new Invalid();
    }

    static RememberMeVerifyResult unavailable() {
        return new Unavailable();
    }
}
