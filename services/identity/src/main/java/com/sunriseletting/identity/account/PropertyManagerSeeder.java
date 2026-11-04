package com.sunriseletting.identity.account;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sunriseletting.identity.account.PropertyManagerSeedProperties.SeededAccount;
import com.sunriseletting.identity.validation.EmailAddress;

/**
 * Creates the configured Property Manager accounts on start-up. An account that already exists is never changed, so
 * starting again creates no duplicates and doesn't undo a changed password. A bad entry doesn't stop the service: it is
 * logged and the rest are still seeded. Temporary passwords are never logged.
 */
@Component
@EnableConfigurationProperties(PropertyManagerSeedProperties.class)
public class PropertyManagerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PropertyManagerSeeder.class);

    private final PropertyManagerSeedProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository repository;

    public PropertyManagerSeeder(PropertyManagerSeedProperties properties, PasswordEncoder passwordEncoder, AccountRepository repository) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        List<SeededAccount> accounts = properties.propertyManagers();
        int created = 0;
        for (int position = 0; position < accounts.size(); position++) {
            if (createIfNew(position, accounts.get(position))) {
                created++;
            }
        }
        log.info("Property Manager accounts seeded: {} created, {} skipped", created, accounts.size() - created);
    }

    /**
     * Inserting first means there's no gap between a check and the insert in which another instance starting up, or a
     * Visitor signing up, could take the email.
     */
    private boolean createIfNew(int position, SeededAccount seeded) {
        if (seeded.email() == null || seeded.email().isBlank() || seeded.temporaryPassword() == null || seeded.temporaryPassword().isEmpty()) {
            log.error("Property Manager account not created: entry {} needs both an email and a temporary password", position);
            return false;
        }

        String email = EmailAddress.normalise(seeded.email());
        NewAccount account = new NewAccount(UUID.randomUUID(), email, null, null, null, null, null,
                passwordEncoder.encode(seeded.temporaryPassword()), Role.PROPERTY_MANAGER, true);
        if (repository.insertIfEmailIsNew(account)) {
            return true;
        }

        repository.findCredentialsByEmail(email)
                .filter(existing -> existing.role() == Role.VISITOR)
                .ifPresent(existing -> log.error("Property Manager account not created: {} already belongs to a Visitor account", email));
        return false;
    }
}
