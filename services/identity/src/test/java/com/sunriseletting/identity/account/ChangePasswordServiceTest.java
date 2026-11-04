package com.sunriseletting.identity.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.validation.AccountFieldError;
import com.sunriseletting.identity.validation.AccountFieldValidator;
import com.sunriseletting.identity.validation.PasswordRules;

/**
 * Uses the real encoder, spied on, because the same-as-temporary check only means something against a real hash.
 */
@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String TEMPORARY_PASSWORD = "Temporary-pw-1";
    private static final String NEW_PASSWORD = "Brand-new-pw-2";

    private final PasswordEncoder encoder = spy(new PreHashingBCryptPasswordEncoder());
    private final String temporaryHash = new PreHashingBCryptPasswordEncoder().encode(TEMPORARY_PASSWORD);

    @Mock
    private AccountRepository repository;

    private ChangePasswordService service() {
        return new ChangePasswordService(new AccountFieldValidator(new PasswordRules()), encoder, repository);
    }

    private Optional<AccountCredentials> accountWithHash(String hash, boolean temporary) {
        return Optional.of(new AccountCredentials(ACCOUNT_ID, hash, Role.PROPERTY_MANAGER, temporary));
    }

    private void verifyNothingWasStored() {
        verify(repository, never().description("the password should not be changed")).replaceTemporaryPassword(any(), any());
    }

    @Test
    void validNewPasswordIsStoredOnlyAsAHash() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));
        when(repository.replaceTemporaryPassword(eq(ACCOUNT_ID), any())).thenReturn(true);

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, NEW_PASSWORD));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verify(encoder, description("the new password should go through the shared encoder")).encode(NEW_PASSWORD);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(repository, description("the new hash should be stored")).replaceTemporaryPassword(eq(ACCOUNT_ID), stored.capture());
        assertTrue(new PreHashingBCryptPasswordEncoder().matches(NEW_PASSWORD, stored.getValue()),
                "the stored value should be a hash of the new password");
        assertEquals(new ChangePasswordResult.Changed(), result, "a valid new password should change the password");
    }

    @Test
    void unknownAccountIsReported() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(Optional.empty());

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, NEW_PASSWORD));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.AccountNotFound(), result, "an unknown account should be reported");
    }

    @Test
    void accountWithoutATemporaryPasswordIsRefused() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, false));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, NEW_PASSWORD));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.NoTemporaryPassword(), result,
                "changing a password at any other time is out of scope");
    }

    @Test
    void accountWithoutATemporaryPasswordIsRefusedEvenWithAnInvalidNewPassword() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, false));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest("short", "other"));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.NoTemporaryPassword(), result,
                "there is nothing to correct on an account that can't change its password");
    }

    @Test
    void anotherChangeGettingThereFirstIsTheSameAsNoTemporaryPassword() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));
        when(repository.replaceTemporaryPassword(eq(ACCOUNT_ID), any())).thenReturn(false);

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, NEW_PASSWORD));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verify(repository, description("the change should be tried")).replaceTemporaryPassword(eq(ACCOUNT_ID), any());
        assertEquals(new ChangePasswordResult.NoTemporaryPassword(), result,
                "if the password stopped being temporary after the look-up, this change should be refused");
    }

    static Stream<Arguments> brokenRules() {
        return Stream.of(
                Arguments.of(null, AccountFieldError.PASSWORD_REQUIRED),
                Arguments.of("", AccountFieldError.PASSWORD_REQUIRED),
                Arguments.of("Aa1!", AccountFieldError.PASSWORD_TOO_SHORT),
                Arguments.of("Aa1!" + "a".repeat(125), AccountFieldError.PASSWORD_TOO_LONG),
                Arguments.of("alllowercase1!", AccountFieldError.PASSWORD_MISSING_CHARACTER_TYPES));
    }

    @ParameterizedTest
    @MethodSource("brokenRules")
    void newPasswordBreakingARuleGivesItsCodeAndChangesNothing(String newPassword, AccountFieldError expected) {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(newPassword, newPassword));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        ChangePasswordResult.Invalid invalid = assertInstanceOf(ChangePasswordResult.Invalid.class, result,
                "a broken rule should be rejected");
        assertEquals(expected, invalid.errors().get("newPassword"), "the new password should have the rule's code");
    }

    @Test
    void newPasswordSameAsTheTemporaryOneIsRejected() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(TEMPORARY_PASSWORD, TEMPORARY_PASSWORD));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verify(encoder, description("the comparison should use the stored hash")).matches(TEMPORARY_PASSWORD, temporaryHash);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.Invalid(Map.of("newPassword", AccountFieldError.PASSWORD_SAME_AS_TEMPORARY)), result,
                "the new password must differ from the temporary one");
    }

    @Test
    void theRulesComeBeforeTheComparisonWithTheTemporaryPassword() {
        // A temporary password needn't meet the rules, and this one has no number or symbol.
        String weakTemporary = "weak-temporary";
        String weakHash = new PreHashingBCryptPasswordEncoder().encode(weakTemporary);
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(weakHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(weakTemporary, weakTemporary));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verify(encoder, never().description("a password that breaks a rule should not reach the comparison")).matches(any(), any());
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.Invalid(Map.of("newPassword", AccountFieldError.PASSWORD_MISSING_CHARACTER_TYPES)), result,
                "the broken rule should be reported rather than the sameness");
    }

    @Test
    void missingConfirmationIsRejected() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, ""));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.Invalid(Map.of("confirmNewPassword", AccountFieldError.CONFIRM_PASSWORD_REQUIRED)), result,
                "the confirmation should be required");
    }

    @Test
    void mismatchedConfirmationIsRejected() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(NEW_PASSWORD, NEW_PASSWORD + "x"));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.Invalid(Map.of("confirmNewPassword", AccountFieldError.PASSWORDS_DO_NOT_MATCH)), result,
                "the confirmation should match the new password");
    }

    @Test
    void bothFieldsAreReportedTogether() {
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(TEMPORARY_PASSWORD, "different"));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verifyNothingWasStored();
        assertEquals(new ChangePasswordResult.Invalid(Map.of(
                        "newPassword", AccountFieldError.PASSWORD_SAME_AS_TEMPORARY,
                        "confirmNewPassword", AccountFieldError.PASSWORDS_DO_NOT_MATCH)), result,
                "each failing field should have its code");
    }

    @Test
    void newPasswordIsNotTrimmed() {
        String withSpaces = " " + TEMPORARY_PASSWORD + " ";
        when(repository.findCredentialsById(ACCOUNT_ID)).thenReturn(accountWithHash(temporaryHash, true));
        when(repository.replaceTemporaryPassword(eq(ACCOUNT_ID), any())).thenReturn(true);

        ChangePasswordResult result = service().change(ACCOUNT_ID, new ChangePasswordRequest(withSpaces, withSpaces));

        verify(repository, description("the account should be looked up by id")).findCredentialsById(ACCOUNT_ID);
        verify(encoder, description("the spaces should count as part of the password")).encode(withSpaces);
        verify(repository, description("the new hash should be stored")).replaceTemporaryPassword(eq(ACCOUNT_ID), any());
        assertEquals(new ChangePasswordResult.Changed(), result, "the spaces make it a different password");
    }
}
