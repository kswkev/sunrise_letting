package com.sunriseletting.identity.account;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sunriseletting.identity.validation.AccountDetails;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;
import com.sunriseletting.identity.validation.AccountFieldValidator;
import com.sunriseletting.identity.validation.UkPhoneNumber;

@Service
public class VisitorAccountService {

    private final AccountFieldValidator validator;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository repository;

    public VisitorAccountService(AccountFieldValidator validator, PasswordEncoder passwordEncoder, AccountRepository repository) {
        this.validator = validator;
        this.passwordEncoder = passwordEncoder;
        this.repository = repository;
    }

    /**
     * Creates a Visitor account if every field is valid and the email is not in use. The password is hashed
     * before the duplicate check, so a duplicate takes as long to answer as a new account and the response
     * time doesn't reveal that the email is registered.
     */
    public CreateVisitorAccountResult create(AccountDetails details) {
        Map<AccountField, AccountFieldError> errors = validator.validateAll(details);
        if (!errors.isEmpty()) {
            return new CreateVisitorAccountResult.Invalid(errors);
        }

        NewAccount account = new NewAccount(
                UUID.randomUUID(),
                details.email().strip().toLowerCase(Locale.ROOT),
                details.title().strip(),
                AccountFieldValidator.isOtherTitle(details.title()) ? details.titleOther().strip() : null,
                details.firstName().strip(),
                details.surname().strip(),
                UkPhoneNumber.toE164(details.phone()).orElseThrow(),
                passwordEncoder.encode(details.password()),
                Role.VISITOR);

        return repository.insertIfEmailIsNew(account)
                ? new CreateVisitorAccountResult.Created(account.id(), account.role())
                : new CreateVisitorAccountResult.EmailInUse();
    }
}
