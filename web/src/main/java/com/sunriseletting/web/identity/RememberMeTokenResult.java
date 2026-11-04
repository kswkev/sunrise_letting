package com.sunriseletting.web.identity;

import java.time.Instant;

/** What identity said about a request for a new remember-me token. */
public sealed interface RememberMeTokenResult {

    /** {@link #toString()} leaves out the token. */
    record Issued(String series, String token, Instant expiresAt) implements RememberMeTokenResult {

        @Override
        public String toString() {
            return "Issued[redacted]";
        }
    }

    /** A Property Manager or an unknown account: nothing to remember. */
    record NotAllowed() implements RememberMeTokenResult {
    }

    /** The request couldn't be completed: a connection error, a timeout, a 5xx or a response outside the contract. */
    record Unavailable() implements RememberMeTokenResult {
    }

    static RememberMeTokenResult notAllowed() {
        return new NotAllowed();
    }

    static RememberMeTokenResult unavailable() {
        return new Unavailable();
    }
}
