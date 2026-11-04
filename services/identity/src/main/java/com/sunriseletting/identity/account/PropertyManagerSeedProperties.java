package com.sunriseletting.identity.account;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Property Manager accounts to seed, supplied by the deployment secret as indexed environment variables such as
 * {@code IDENTITY_PROPERTY_MANAGERS_0_EMAIL} and {@code IDENTITY_PROPERTY_MANAGERS_0_TEMPORARY_PASSWORD}.
 */
@ConfigurationProperties("identity")
public record PropertyManagerSeedProperties(@DefaultValue List<SeededAccount> propertyManagers) {

    public record SeededAccount(String email, String temporaryPassword) {

        /** Hides the temporary password, so it can never reach a log. */
        @Override
        public String toString() {
            return "SeededAccount[email=" + email + "]";
        }
    }
}
