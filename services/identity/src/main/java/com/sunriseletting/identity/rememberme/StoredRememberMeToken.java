package com.sunriseletting.identity.rememberme;

import java.time.Instant;
import java.util.UUID;

import com.sunriseletting.identity.account.Role;

/**
 * A stored remember-me login and the role of its account.
 */
public record StoredRememberMeToken(String series, UUID accountId, Role role, Instant expiresAt) {
}
