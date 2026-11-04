package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.account.PropertyManagerSeedProperties.SeededAccount;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PropertyManagerSeederTest {

    private static final String PASSWORD = "temporary-Secret";
    private static final String EMAIL = "pat@example.com";

    @Mock
    private AccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PropertyManagerSeeder seeder(SeededAccount... accounts) {
        return new PropertyManagerSeeder(new PropertyManagerSeedProperties(List.of(accounts)), passwordEncoder, repository);
    }

    private static AccountCredentials existing(Role role) {
        return new AccountCredentials(UUID.randomUUID(), "existing-hash", role, false);
    }

    @Test
    void newEmailIsStoredNormalisedAsATemporaryPropertyManagerWithOnlyTheHash() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true);

        seeder(new SeededAccount("  Pat@Example.COM ", PASSWORD)).seed();

        verify(passwordEncoder, description("the password should be hashed by the shared encoder")).encode(PASSWORD);
        ArgumentCaptor<NewAccount> inserted = ArgumentCaptor.forClass(NewAccount.class);
        verify(repository, description("the account should be stored")).insertIfEmailIsNew(inserted.capture());
        NewAccount account = inserted.getValue();
        assertNotNull(account.id(), "the account should have an id");
        assertEquals(EMAIL, account.email(), "the email should be stored trimmed and lower-cased");
        assertEquals("the-hash", account.passwordHash(), "only the hash of the password should be stored");
        assertEquals(Role.PROPERTY_MANAGER, account.role(), "the account should have the Property Manager role only");
        assertTrue(account.passwordTemporary(), "the password should be marked as temporary");
        assertNull(account.title(), "a Property Manager has no title");
        assertNull(account.titleOther(), "a Property Manager has no free-text title");
        assertNull(account.firstName(), "a Property Manager has no first name");
        assertNull(account.surname(), "a Property Manager has no surname");
        assertNull(account.phone(), "a Property Manager has no phone");
    }

    @Test
    void existingPropertyManagerIsSkippedWithoutAnError(CapturedOutput output) {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(false);
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(existing(Role.PROPERTY_MANAGER)));

        seeder(new SeededAccount(EMAIL, PASSWORD)).seed();

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        verify(repository, description("the insert should be tried")).insertIfEmailIsNew(any());
        verify(repository, description("the existing account's role should be checked")).findCredentialsByEmail(EMAIL);
        assertThat(output.getOut()).as("the counts should be logged").contains("0 created, 1 skipped");
        assertThat(output.getAll()).as("an account that is already there is not a problem").doesNotContain("ERROR");
    }

    @Test
    void emailUsedByAVisitorLogsAnErrorGivingThePositionNotTheEmailAndTheNextAccountIsStillCreated(CapturedOutput output) {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(passwordEncoder.encode("second-Secret")).thenReturn("second-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true, false, true);
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(existing(Role.VISITOR)));

        seeder(new SeededAccount("kim@example.com", "second-Secret"), new SeededAccount(EMAIL, PASSWORD),
                new SeededAccount("lee@example.com", "second-Secret")).seed();

        verify(passwordEncoder, description("the clashing entry's password should be hashed")).encode(PASSWORD);
        verify(passwordEncoder, times(2).description("the other passwords should be hashed")).encode("second-Secret");
        verify(repository, times(3).description("seeding should carry on to the next account")).insertIfEmailIsNew(any());
        verify(repository, description("the existing account's role should be checked")).findCredentialsByEmail(EMAIL);
        assertThat(output.getOut()).as("the clash should be an error giving the entry's position")
                .contains("ERROR")
                .contains("entry 1 has an email already used by a Visitor account, so it was not changed");
        assertThat(output.getAll()).as("the email is personal data and should not be logged").doesNotContain(EMAIL);
        assertThat(output.getOut()).as("the counts should be logged").contains("2 created, 1 skipped");
    }

    @Test
    void temporaryPasswordsAreNeverLogged(CapturedOutput output) {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(false);
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(existing(Role.VISITOR)));

        seeder(new SeededAccount(EMAIL, PASSWORD), new SeededAccount("", PASSWORD)).seed();

        verify(passwordEncoder, description("the password should be hashed")).encode(PASSWORD);
        verify(repository, description("the insert should be tried")).insertIfEmailIsNew(any());
        verify(repository, description("the existing account's role should be checked")).findCredentialsByEmail(EMAIL);
        assertThat(output.getOut()).as("the capture should see the seeder's logs").contains("0 created, 2 skipped");
        assertThat(output.getAll()).as("the temporary password should not be logged").doesNotContain(PASSWORD);
    }

    @Test
    void entryWithoutAnEmailOrPasswordIsSkippedWithAnErrorGivingItsPosition(CapturedOutput output) {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("the-hash");
        when(repository.insertIfEmailIsNew(any())).thenReturn(true);

        seeder(new SeededAccount("   ", PASSWORD),
                new SeededAccount(null, PASSWORD),
                new SeededAccount(EMAIL, ""),
                new SeededAccount(EMAIL, null),
                new SeededAccount("lee@example.com", PASSWORD)).seed();

        verify(passwordEncoder, times(1).description("only the complete entry should be hashed")).encode(any());
        verify(repository, times(1).description("only the complete entry should be stored")).insertIfEmailIsNew(any());
        for (int position = 0; position < 4; position++) {
            assertThat(output.getOut()).as("entry " + position + " should be reported by its position")
                    .contains("entry " + position + " needs both an email and a temporary password");
        }
        assertThat(output.getOut()).as("the counts should be logged").contains("1 created, 4 skipped");
    }

    @Test
    void nothingConfiguredTouchesNothing(CapturedOutput output) {
        seeder().seed();

        verify(repository, never().description("there is nothing to store")).insertIfEmailIsNew(any());
        verify(passwordEncoder, never().description("there is nothing to hash")).encode(any());
        assertThat(output.getOut()).as("the counts should be logged").contains("0 created, 0 skipped");
    }
}
