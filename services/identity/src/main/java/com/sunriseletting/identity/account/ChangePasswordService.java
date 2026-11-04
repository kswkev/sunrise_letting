package com.sunriseletting.identity.account;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;
import com.sunriseletting.identity.validation.AccountFieldValidator;

/**
 * Replaces a temporary password at first sign-in. Changing a password at any other time is out of scope, so an account
 * without a temporary password is refused whatever was submitted.
 */
@Service
public class ChangePasswordService {

    private static final String NEW_PASSWORD_FIELD = "newPassword";
    private static final String CONFIRM_NEW_PASSWORD_FIELD = "confirmNewPassword";

    private final AccountFieldValidator validator;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository repository;

    public ChangePasswordService(AccountFieldValidator validator, PasswordEncoder passwordEncoder, AccountRepository repository) {
        this.validator = validator;
        this.passwordEncoder = passwordEncoder;
        this.repository = repository;
    }

    public ChangePasswordResult change(UUID accountId, ChangePasswordRequest request) {
        Optional<AccountCredentials> found = repository.findCredentialsById(accountId);
        if (found.isEmpty()) {
            return new ChangePasswordResult.AccountNotFound();
        }
        AccountCredentials account = found.get();
        if (!account.passwordTemporary()) {
            return new ChangePasswordResult.NoTemporaryPassword();
        }

        Map<String, AccountFieldError> errors = validate(request, account.passwordHash());
        if (!errors.isEmpty()) {
            return new ChangePasswordResult.Invalid(errors);
        }

        return repository.replaceTemporaryPassword(accountId, passwordEncoder.encode(request.newPassword()))
                ? new ChangePasswordResult.Changed()
                : new ChangePasswordResult.NoTemporaryPassword();
    }

    private Map<String, AccountFieldError> validate(ChangePasswordRequest request, String temporaryHash) {
        Map<String, AccountFieldError> errors = new LinkedHashMap<>();
        validator.validate(AccountField.PASSWORD, request.newPassword(), null)
                .or(() -> sameAsTemporary(request.newPassword(), temporaryHash))
                .ifPresent(error -> errors.put(NEW_PASSWORD_FIELD, error));
        validator.validate(AccountField.CONFIRM_PASSWORD, request.confirmNewPassword(), request.newPassword())
                .ifPresent(error -> errors.put(CONFIRM_NEW_PASSWORD_FIELD, error));
        return errors;
    }

    private Optional<AccountFieldError> sameAsTemporary(String newPassword, String temporaryHash) {
        return passwordEncoder.matches(newPassword, temporaryHash)
                ? Optional.of(AccountFieldError.PASSWORD_SAME_AS_TEMPORARY)
                : Optional.empty();
    }
}
