package com.sunriseletting.identity.validation;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * The password rules, applied in order, giving the first one that fails. Passwords are never trimmed.
 */
@Component
public class PasswordRules {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    public Optional<AccountFieldError> check(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of(AccountFieldError.PASSWORD_REQUIRED);
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return Optional.of(AccountFieldError.PASSWORD_TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            return Optional.of(AccountFieldError.PASSWORD_TOO_LONG);
        }
        if (!hasEveryCharacterType(password)) {
            return Optional.of(AccountFieldError.PASSWORD_MISSING_CHARACTER_TYPES);
        }
        return Optional.empty();
    }

    private static boolean hasEveryCharacterType(String password) {
        return password.codePoints().anyMatch(Character::isUpperCase)
                && password.codePoints().anyMatch(Character::isLowerCase)
                && password.codePoints().anyMatch(Character::isDigit)
                && password.codePoints().anyMatch(PasswordRules::isSymbol);
    }

    private static boolean isSymbol(int codePoint) {
        return !Character.isLetter(codePoint)
                && !Character.isDigit(codePoint)
                && !Character.isWhitespace(codePoint)
                && !Character.isSpaceChar(codePoint);
    }
}
