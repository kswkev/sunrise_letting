package com.sunriseletting.identity.rememberme;

import java.time.Instant;
import java.util.UUID;

/**
 * A remember-me login ready to store, with the token already hashed.
 */
public record NewRememberMeToken(String series, String tokenHash, UUID accountId, Instant expiresAt) {
}
