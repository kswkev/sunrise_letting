package com.sunriseletting.identity.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.validation.AccountDetails;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;
import com.sunriseletting.identity.validation.AccountFieldValidator;
import com.sunriseletting.identity.validation.PasswordRules;

@ExtendWith(MockitoExtension.class)
class VisitorAccountServiceTest {

    private static final String PASSWORD = "Abcdefg1!";

    @Mock
    private AccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private VisitorAccountService service() {
        return new VisitorAccountService(new AccountFieldValidator(new PasswordRules()), passwordEncoder, repository);
    }

    private static AccountDetails details(String title, String titleOther, String email) {
        return new AccountDetails(title, titleOther, " Jo ", " Smith ", email, "07700 900123", PASSWORD, PASSWORD);
    }

    private NewAccount verifyInsertedAccount() {
        ArgumentCaptor<NewAccount> inserted = ArgumentCaptor.forClass(NewAccount.class);
        verify(repository, description("the account should be stored")).insertIfEmailIsNew(inserted.capture());
        return inserted.getValue();
    }

    @Test
    void validDetailsAreStoredNormalisedWithAHashedPasswordAndTheVisitorRole() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true);

        CreateVisitorAccountResult result = service().create(details(" Mr ", null, " Jo@Example.com "));

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        NewAccount stored = verifyInsertedAccount();
        assertEquals("jo@example.com", stored.email(), "the email should be stored trimmed and lower-cased");
        assertEquals("+447700900123", stored.phone(), "the phone should be stored in E.164");
        assertEquals("the-hash", stored.passwordHash(), "only the hash of the password should be stored");
        assertEquals(Role.VISITOR, stored.role(), "the account should have the Visitor role");
        assertFalse(stored.passwordTemporary(), "a Visitor chooses their own password, so it isn't temporary");
        assertEquals("Mr", stored.title(), "the title should be stored trimmed");
        assertEquals("Jo", stored.firstName(), "the first name should be stored trimmed");
        assertEquals("Smith", stored.surname(), "the surname should be stored trimmed");
        CreateVisitorAccountResult.Created created = assertInstanceOf(CreateVisitorAccountResult.Created.class, result,
                "valid details should create an account");
        assertEquals(stored.id(), created.accountId(), "the result should identify the stored account");
        assertEquals(Role.VISITOR, created.role(), "the result should give the Visitor role");
    }

    @Test
    void otherTitleTextIsStoredTrimmedWhenTitleIsOther() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true);

        service().create(details("Other", " Rev ", "jo@example.com"));

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        assertEquals("Rev", verifyInsertedAccount().titleOther(), "the free-text title should be stored trimmed");
    }

    @Test
    void otherTitleTextIsNotStoredWhenTitleIsNotOther() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true);

        service().create(details("Mr", "xyz", "jo@example.com"));

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        assertNull(verifyInsertedAccount().titleOther(), "the free-text title should be ignored when Title is not Other");
    }

    @Test
    void emailAlreadyInUseCreatesNoAccount() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(false);

        CreateVisitorAccountResult result = service().create(details("Mr", null, "jo@example.com"));

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        assertEquals("jo@example.com", verifyInsertedAccount().email(), "the insert should be tried with the normalised email");
        assertInstanceOf(CreateVisitorAccountResult.EmailInUse.class, result,
                "an email already in use should not create an account");
    }

    @Test
    void invalidDetailsAreRejectedWithoutTouchingTheDataStore() {
        AccountDetails invalid = details("Mr", null, "not-an-email");

        CreateVisitorAccountResult result = service().create(invalid);

        CreateVisitorAccountResult.Invalid rejected = assertInstanceOf(CreateVisitorAccountResult.Invalid.class, result,
                "invalid details should be rejected");
        assertEquals(Map.of(AccountField.EMAIL, AccountFieldError.EMAIL_INVALID), rejected.errors(),
                "the failing field should be reported");
        verify(repository, never().description("invalid details should not be stored or checked for a duplicate"))
                .insertIfEmailIsNew(any());
        verify(passwordEncoder, never().description("invalid details should not be hashed")).encode(any());
    }
}
