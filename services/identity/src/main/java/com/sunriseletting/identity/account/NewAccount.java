package com.sunriseletting.identity.account;

import java.util.UUID;

/**
 * An account ready to store: every value normalised and the password already hashed.
 */
public record NewAccount(
        UUID id,
        String email,
        String title,
        String titleOther,
        String firstName,
        String surname,
        String phone,
        String passwordHash,
        Role role,
        boolean passwordTemporary) {

    /** Hides every value, so the personal details and the password hash can never reach a log. */
    @Override
    public String toString() {
        return "NewAccount[hidden]";
    }
}
