package com.sunriseletting.identity.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.account.AccountCredentials;
import com.sunriseletting.identity.account.AccountRepository;
import com.sunriseletting.identity.account.PreHashingBCryptPasswordEncoder;
import com.sunriseletting.identity.account.Role;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;

/**
 * Uses the real encoder, spied on, because the point of several of these tests is that a stored hash and the dummy
 * hash really go through it.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "jo@example.com";
    private static final String PASSWORD = "Secret-pw-1";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private final PasswordEncoder encoder = spy(new PreHashingBCryptPasswordEncoder());

    @Mock
    private AccountRepository repository;

    private AuthenticationService service() {
        return new AuthenticationService(repository, encoder);
    }

    private Optional<AccountCredentials> storedAccount(String password, Role role) {
        return Optional.of(new AccountCredentials(ACCOUNT_ID, new PreHashingBCryptPasswordEncoder().encode(password), role));
    }

    @Test
    void correctPasswordGivesTheAccountIdAndRole() {
        Optional<AccountCredentials> account = storedAccount(PASSWORD, Role.VISITOR);
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(account);

        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

        verify(repository, description("the account should be looked up by email")).findCredentialsByEmail(EMAIL);
        verify(encoder, description("the password should be checked against the stored hash"))
                .matches(PASSWORD, account.orElseThrow().passwordHash());
        assertEquals(new AuthenticationResult.Authenticated(ACCOUNT_ID, Role.VISITOR), result,
                "the correct password should give the account's id and role");
    }

    @Test
    void anyRoleCanAuthenticate() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(storedAccount(PASSWORD, Role.PROPERTY_MANAGER));

        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

        verify(repository, description("the account should be looked up by email")).findCredentialsByEmail(EMAIL);
        assertEquals(new AuthenticationResult.Authenticated(ACCOUNT_ID, Role.PROPERTY_MANAGER), result,
                "a Property Manager should get their own role");
    }

    @Test
    void emailIsNormalisedBeforeTheLookup() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(storedAccount(PASSWORD, Role.VISITOR));

        AuthenticationResult result = service().authenticate(new AuthenticationRequest("  JO@example.COM ", PASSWORD));

        verify(repository, description("the lookup should use the trimmed, lower-cased email")).findCredentialsByEmail(EMAIL);
        assertInstanceOf(AuthenticationResult.Authenticated.class, result,
                "capitals and surrounding spaces should not stop the sign-in");
    }

    @Test
    void wrongPasswordGivesInvalidCredentials() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(storedAccount(PASSWORD, Role.VISITOR));

        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, "Wrong-pw-1"));

        verify(repository, description("the account should be looked up by email")).findCredentialsByEmail(EMAIL);
        assertEquals(new AuthenticationResult.InvalidCredentials(), result, "a wrong password should be rejected");
    }

    @Test
    void unknownEmailStillComparesThePasswordAgainstAFullCostHash() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.empty());

        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

        verify(repository, description("the account should be looked up by email")).findCredentialsByEmail(EMAIL);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(encoder, description("an unknown email should still cost a password comparison"))
                .matches(eq(PASSWORD), hash.capture());
        String realHash = new PreHashingBCryptPasswordEncoder().encode(PASSWORD);
        // "$2a$10$": anything else would make BCrypt reject it at once, or run at a different cost.
        assertEquals(realHash.substring(0, 7), hash.getValue().substring(0, 7),
                "the dummy hash should have the same version and cost as a stored hash");
        assertEquals(realHash.length(), hash.getValue().length(), "the dummy hash should be a complete BCrypt hash");
        assertEquals(new AuthenticationResult.InvalidCredentials(), result, "an unknown email should be rejected");
    }

    @Test
    void passwordIsNotTrimmed() {
        Optional<AccountCredentials> account = storedAccount(" " + PASSWORD + " ", Role.VISITOR);
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(account);

        AuthenticationResult untrimmed = service().authenticate(new AuthenticationRequest(EMAIL, " " + PASSWORD + " "));
        AuthenticationResult trimmed = service().authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

        verify(repository, times(2).description("each attempt should look up the account"))
                .findCredentialsByEmail(EMAIL);
        assertInstanceOf(AuthenticationResult.Authenticated.class, untrimmed, "the spaces should count as part of the password");
        assertEquals(new AuthenticationResult.InvalidCredentials(), trimmed, "the password without its spaces should not match");
    }

    @Test
    void passwordOfSpacesIsCheckedRatherThanTreatedAsEmpty() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(storedAccount(PASSWORD, Role.VISITOR));

        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, "   "));

        verify(repository, description("the account should be looked up by email")).findCredentialsByEmail(EMAIL);
        assertEquals(new AuthenticationResult.InvalidCredentials(), result, "a password of spaces is just a wrong password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void emptyEmailIsReportedWithoutCheckingAnything(String email) {
        AuthenticationResult result = service().authenticate(new AuthenticationRequest(email, PASSWORD));

        AuthenticationResult.Invalid invalid = assertInstanceOf(AuthenticationResult.Invalid.class, result,
                "an empty email should be rejected");
        assertEquals(Map.of(AccountField.EMAIL, AccountFieldError.EMAIL_REQUIRED), invalid.errors(),
                "only the email should be reported");
        verifyNothingWasChecked();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyPasswordIsReportedWithoutCheckingAnything(String password) {
        AuthenticationResult result = service().authenticate(new AuthenticationRequest(EMAIL, password));

        AuthenticationResult.Invalid invalid = assertInstanceOf(AuthenticationResult.Invalid.class, result,
                "an empty password should be rejected");
        assertEquals(Map.of(AccountField.PASSWORD, AccountFieldError.PASSWORD_REQUIRED), invalid.errors(),
                "only the password should be reported");
        verifyNothingWasChecked();
    }

    @Test
    void bothEmptyAreBothReported() {
        AuthenticationResult result = service().authenticate(new AuthenticationRequest("", ""));

        AuthenticationResult.Invalid invalid = assertInstanceOf(AuthenticationResult.Invalid.class, result,
                "empty fields should be rejected");
        assertThat(invalid.errors()).as("both fields should be reported").isEqualTo(Map.of(
                AccountField.EMAIL, AccountFieldError.EMAIL_REQUIRED,
                AccountField.PASSWORD, AccountFieldError.PASSWORD_REQUIRED));
        verifyNothingWasChecked();
    }

    private void verifyNothingWasChecked() {
        verify(repository, never().description("empty fields should not reach the data store")).findCredentialsByEmail(any());
        verify(encoder, never().description("empty fields should not reach the encoder")).matches(any(), any());
    }
}
