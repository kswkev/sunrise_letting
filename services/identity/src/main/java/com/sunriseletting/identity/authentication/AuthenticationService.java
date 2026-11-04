package com.sunriseletting.identity.authentication;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sunriseletting.identity.account.AccountCredentials;
import com.sunriseletting.identity.account.AccountRepository;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;
import com.sunriseletting.identity.validation.EmailAddress;

@Service
public class AuthenticationService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthenticationService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        // Made by the encoder, so it always has the same format and cost as a stored hash. Nobody knows its password.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Checks the password against the account with that email. An unknown email is checked against a dummy hash, so
     * it takes as long to answer as a wrong password and the response time doesn't reveal that the email is registered.
     */
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        Map<AccountField, AccountFieldError> errors = emptyFields(request);
        if (!errors.isEmpty()) {
            return new AuthenticationResult.Invalid(errors);
        }

        Optional<AccountCredentials> account = repository.findCredentialsByEmail(EmailAddress.normalise(request.email()));
        String hash = account.map(AccountCredentials::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        return account.filter(found -> passwordMatches)
                .<AuthenticationResult>map(found -> new AuthenticationResult.Authenticated(found.id(), found.role(), found.passwordTemporary()))
                .orElseGet(AuthenticationResult.InvalidCredentials::new);
    }

    /** Only whether the fields are filled in: the sign-up format rules don't apply to signing in. Passwords are never trimmed. */
    private static Map<AccountField, AccountFieldError> emptyFields(AuthenticationRequest request) {
        Map<AccountField, AccountFieldError> errors = new EnumMap<>(AccountField.class);
        if (request.email() == null || request.email().isBlank()) {
            errors.put(AccountField.EMAIL, AccountFieldError.EMAIL_REQUIRED);
        }
        if (request.password() == null || request.password().isEmpty()) {
            errors.put(AccountField.PASSWORD, AccountFieldError.PASSWORD_REQUIRED);
        }
        return errors;
    }
}
