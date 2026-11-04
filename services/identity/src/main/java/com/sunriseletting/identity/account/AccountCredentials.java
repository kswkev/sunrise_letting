package com.sunriseletting.identity.account;

import java.util.UUID;

/**
 * What a stored account needs for checking a password.
 */
public record AccountCredentials(UUID id, String passwordHash, Role role, boolean passwordTemporary) {

    /** Hides the hash, so it can never reach a log. */
    @Override
    public String toString() {
        return "AccountCredentials[id=" + id + ", role=" + role + "]";
    }
}
