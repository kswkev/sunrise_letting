package com.sunriseletting.identity.validation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * The sign-up field rules. For each field it applies the rules in order and gives only the first that fails.
 * Everything except the passwords is trimmed first, and lengths are counted in Unicode characters.
 */
@Component
public class AccountFieldValidator {

    private static final String OTHER_TITLE = "Other";
    private static final Set<String> TITLES = Set.of("Mr", "Mrs", "Miss", "Ms", "Mx", "Dr", OTHER_TITLE);
    private static final int TITLE_OTHER_MAX_LENGTH = 20;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 254;

    /** One @, a non-empty local part, a domain containing a dot, and no whitespace. Not full RFC 5322. */
    private static final Pattern EMAIL =
            Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final PasswordRules passwordRules;

    public AccountFieldValidator(PasswordRules passwordRules) {
        this.passwordRules = passwordRules;
    }

    /**
     * Checks one field on its own. Other title is only checked for length, because whether it is needed
     * depends on Title. Nothing here looks up whether an account exists.
     *
     * @param password the password to compare with, only used for {@link AccountField#CONFIRM_PASSWORD}
     */
    public Optional<AccountFieldError> validate(AccountField field, String value, String password) {
        return switch (field) {
            case TITLE -> validateTitle(value);
            case TITLE_OTHER -> tooLong(value, TITLE_OTHER_MAX_LENGTH, AccountFieldError.TITLE_OTHER_TOO_LONG);
            case FIRST_NAME -> validateName(value, AccountFieldError.FIRST_NAME_REQUIRED, AccountFieldError.FIRST_NAME_TOO_LONG);
            case SURNAME -> validateName(value, AccountFieldError.SURNAME_REQUIRED, AccountFieldError.SURNAME_TOO_LONG);
            case EMAIL -> validateEmail(value);
            case PHONE -> validatePhone(value);
            case PASSWORD -> passwordRules.check(value);
            case CONFIRM_PASSWORD -> validateConfirmPassword(value, password);
        };
    }

    /**
     * Checks a whole submission, giving one error per failing field. Other title is only checked when Title
     * is Other, when it is also required.
     */
    public Map<AccountField, AccountFieldError> validateAll(AccountDetails details) {
        Map<AccountField, AccountFieldError> errors = new EnumMap<>(AccountField.class);
        validate(AccountField.TITLE, details.title(), null).ifPresent(error -> errors.put(AccountField.TITLE, error));
        if (isOtherTitle(details.title())) {
            validateTitleOtherWhenNeeded(details.titleOther()).ifPresent(error -> errors.put(AccountField.TITLE_OTHER, error));
        }
        validate(AccountField.FIRST_NAME, details.firstName(), null).ifPresent(error -> errors.put(AccountField.FIRST_NAME, error));
        validate(AccountField.SURNAME, details.surname(), null).ifPresent(error -> errors.put(AccountField.SURNAME, error));
        validate(AccountField.EMAIL, details.email(), null).ifPresent(error -> errors.put(AccountField.EMAIL, error));
        validate(AccountField.PHONE, details.phone(), null).ifPresent(error -> errors.put(AccountField.PHONE, error));
        validate(AccountField.PASSWORD, details.password(), null).ifPresent(error -> errors.put(AccountField.PASSWORD, error));
        validate(AccountField.CONFIRM_PASSWORD, details.confirmPassword(), details.password())
                .ifPresent(error -> errors.put(AccountField.CONFIRM_PASSWORD, error));
        return errors;
    }

    /** Whether this title value, as submitted, means the free-text title applies. */
    public static boolean isOtherTitle(String title) {
        return title != null && OTHER_TITLE.equals(title.strip());
    }

    private static Optional<AccountFieldError> validateTitleOtherWhenNeeded(String value) {
        return isBlank(value)
                ? Optional.of(AccountFieldError.TITLE_OTHER_REQUIRED)
                : tooLong(value, TITLE_OTHER_MAX_LENGTH, AccountFieldError.TITLE_OTHER_TOO_LONG);
    }

    private static Optional<AccountFieldError> validateTitle(String value) {
        if (isBlank(value)) {
            return Optional.of(AccountFieldError.TITLE_REQUIRED);
        }
        return TITLES.contains(value.strip()) ? Optional.empty() : Optional.of(AccountFieldError.TITLE_INVALID);
    }

    private static Optional<AccountFieldError> validateName(String value, AccountFieldError required, AccountFieldError tooLong) {
        return isBlank(value) ? Optional.of(required) : tooLong(value, NAME_MAX_LENGTH, tooLong);
    }

    private static Optional<AccountFieldError> validateEmail(String value) {
        if (isBlank(value)) {
            return Optional.of(AccountFieldError.EMAIL_REQUIRED);
        }
        Optional<AccountFieldError> tooLong = tooLong(value, EMAIL_MAX_LENGTH, AccountFieldError.EMAIL_TOO_LONG);
        if (tooLong.isPresent()) {
            return tooLong;
        }
        return EMAIL.matcher(value.strip()).matches() ? Optional.empty() : Optional.of(AccountFieldError.EMAIL_INVALID);
    }

    private static Optional<AccountFieldError> validatePhone(String value) {
        if (isBlank(value)) {
            return Optional.of(AccountFieldError.PHONE_REQUIRED);
        }
        return UkPhoneNumber.toE164(value).isPresent() ? Optional.empty() : Optional.of(AccountFieldError.PHONE_INVALID);
    }

    private static Optional<AccountFieldError> validateConfirmPassword(String value, String password) {
        if (value == null || value.isEmpty()) {
            return Optional.of(AccountFieldError.CONFIRM_PASSWORD_REQUIRED);
        }
        return value.equals(password) ? Optional.empty() : Optional.of(AccountFieldError.PASSWORDS_DO_NOT_MATCH);
    }

    private static Optional<AccountFieldError> tooLong(String value, int maxLength, AccountFieldError error) {
        if (value == null) {
            return Optional.empty();
        }
        String stripped = value.strip();
        return stripped.codePointCount(0, stripped.length()) > maxLength ? Optional.of(error) : Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
