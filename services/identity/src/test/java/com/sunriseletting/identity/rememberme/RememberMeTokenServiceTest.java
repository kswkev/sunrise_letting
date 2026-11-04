package com.sunriseletting.identity.rememberme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

import com.sunriseletting.identity.account.AccountRepository;
import com.sunriseletting.identity.account.Role;

@ExtendWith(MockitoExtension.class)
class RememberMeTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30.123456789Z");
    private static final Instant NOW_AS_STORED = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String SERIES = "aaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOKEN = "the-current-token";

    @Mock
    private RememberMeTokenRepository tokens;

    @Mock
    private AccountRepository accounts;

    private RememberMeTokenService service() {
        return new RememberMeTokenService(tokens, accounts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Optional<StoredRememberMeToken> stored(Instant expiresAt) {
        return Optional.of(new StoredRememberMeToken(SERIES, ACCOUNT_ID, Role.VISITOR, expiresAt));
    }

    @Test
    void visitorGetsANewSeriesWithOnlyTheHashOfTheTokenStored() {
        when(accounts.findRoleById(ACCOUNT_ID)).thenReturn(Optional.of(Role.VISITOR));

        IssuedRememberMeToken issued = service().issue(ACCOUNT_ID).orElseThrow();

        verify(accounts, description("the account's role should be checked")).findRoleById(ACCOUNT_ID);
        ArgumentCaptor<NewRememberMeToken> inserted = ArgumentCaptor.forClass(NewRememberMeToken.class);
        verify(tokens, description("the new series should be stored")).insert(inserted.capture());
        NewRememberMeToken row = inserted.getValue();
        assertEquals(issued.series(), row.series(), "the response should give the stored series");
        assertEquals(RememberMeTokenValues.hash(issued.token()), row.tokenHash(), "the hash of the returned token should be stored");
        assertNotEquals(issued.token(), row.tokenHash(), "the raw token should not be stored");
        assertEquals(ACCOUNT_ID, row.accountId(), "the series should belong to the account");
        assertEquals(NOW_AS_STORED.plus(Duration.ofDays(30)), row.expiresAt(), "the series should expire in 30 days");
        assertEquals(row.expiresAt(), issued.expiresAt(), "the response should give the stored expiry");
    }

    @Test
    void propertyManagerGetsNoToken() {
        when(accounts.findRoleById(ACCOUNT_ID)).thenReturn(Optional.of(Role.PROPERTY_MANAGER));

        Optional<IssuedRememberMeToken> issued = service().issue(ACCOUNT_ID);

        verify(accounts, description("the account's role should be checked")).findRoleById(ACCOUNT_ID);
        assertTrue(issued.isEmpty(), "a Property Manager should not get a token");
        verify(tokens, never().description("nothing should be stored for a Property Manager")).insert(any());
    }

    @Test
    void unknownAccountGetsNoToken() {
        when(accounts.findRoleById(ACCOUNT_ID)).thenReturn(Optional.empty());

        Optional<IssuedRememberMeToken> issued = service().issue(ACCOUNT_ID);

        verify(accounts, description("the account's role should be checked")).findRoleById(ACCOUNT_ID);
        assertTrue(issued.isEmpty(), "an unknown account should not get a token");
        verify(tokens, never().description("nothing should be stored for an unknown account")).insert(any());
    }

    @Test
    void missingAccountIdGetsNoToken() {
        Optional<IssuedRememberMeToken> issued = service().issue(null);

        assertTrue(issued.isEmpty(), "a request without an account should not get a token");
        verify(accounts, never().description("there is no account to look up")).findRoleById(any());
        verify(tokens, never().description("nothing should be stored")).insert(any());
    }

    @Test
    void validTokenIsSwappedForANewOneAndTheExpiryStaysTheSame() {
        Instant expiresAt = NOW_AS_STORED.plus(Duration.ofDays(10));
        when(tokens.find(SERIES)).thenReturn(stored(expiresAt));
        when(tokens.swapToken(eq(SERIES), eq(RememberMeTokenValues.hash(TOKEN)), anyString(), eq(NOW_AS_STORED))).thenReturn(true);

        VerifiedRememberMeToken verified = service().verify(SERIES, TOKEN).orElseThrow();

        verify(tokens, description("the series should be looked up")).find(SERIES);
        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(tokens, description("the token should be swapped only if the presented one is current"))
                .swapToken(eq(SERIES), eq(RememberMeTokenValues.hash(TOKEN)), newHash.capture(), eq(NOW_AS_STORED));
        assertEquals(RememberMeTokenValues.hash(verified.token()), newHash.getValue(), "the hash of the returned token should be stored");
        assertNotEquals(TOKEN, verified.token(), "the response should give a new token");
        assertEquals(ACCOUNT_ID, verified.accountId(), "the response should give the account");
        assertEquals(Role.VISITOR, verified.role(), "the response should give the account's role");
        assertEquals(expiresAt, verified.expiresAt(), "using a token should not extend the 30 days");
        verify(tokens, never().description("a valid token should not revoke anything")).deleteAllForAccount(any());
    }

    @Test
    void tokenIsStillValidJustBeforeItExpires() {
        when(tokens.find(SERIES)).thenReturn(stored(NOW_AS_STORED.plus(1, ChronoUnit.MICROS)));
        when(tokens.swapToken(eq(SERIES), eq(RememberMeTokenValues.hash(TOKEN)), anyString(), eq(NOW_AS_STORED))).thenReturn(true);

        Optional<VerifiedRememberMeToken> verified = service().verify(SERIES, TOKEN);

        verify(tokens, description("the series should be looked up")).find(SERIES);
        verify(tokens, description("the token should be swapped"))
                .swapToken(eq(SERIES), eq(RememberMeTokenValues.hash(TOKEN)), anyString(), eq(NOW_AS_STORED));
        assertTrue(verified.isPresent(), "a token should work until its expiry");
    }

    @Test
    void expiredSeriesIsRejectedEvenWithAWrongTokenAndRevokesNothing() {
        when(tokens.find(SERIES)).thenReturn(stored(NOW_AS_STORED));

        Optional<VerifiedRememberMeToken> verified = service().verify(SERIES, "any-token");

        verify(tokens, description("the series should be looked up")).find(SERIES);
        assertTrue(verified.isEmpty(), "an expired series should be rejected from the moment of its expiry");
        verify(tokens, never().description("an expired series should not be swapped")).swapToken(any(), any(), any(), any());
        verify(tokens, never().description("an expired series should not count as theft")).deleteAllForAccount(any());
    }

    @Test
    void unknownSeriesIsRejectedAndRevokesNothing() {
        when(tokens.find(SERIES)).thenReturn(Optional.empty());

        Optional<VerifiedRememberMeToken> verified = service().verify(SERIES, TOKEN);

        verify(tokens, description("the series should be looked up")).find(SERIES);
        assertTrue(verified.isEmpty(), "an unknown series should be rejected");
        verify(tokens, never().description("an unknown series should not be swapped")).swapToken(any(), any(), any(), any());
        verify(tokens, never().description("an unknown series should not count as theft")).deleteAllForAccount(any());
    }

    @Test
    void wrongTokenForAKnownSeriesRevokesEveryTokenOfTheAccount() {
        when(tokens.find(SERIES)).thenReturn(stored(NOW_AS_STORED.plus(Duration.ofDays(10))));
        when(tokens.swapToken(eq(SERIES), eq(RememberMeTokenValues.hash("wrong-token")), anyString(), eq(NOW_AS_STORED))).thenReturn(false);

        Optional<VerifiedRememberMeToken> verified = service().verify(SERIES, "wrong-token");

        verify(tokens, description("the series should be looked up")).find(SERIES);
        verify(tokens, description("the swap should be refused"))
                .swapToken(eq(SERIES), eq(RememberMeTokenValues.hash("wrong-token")), anyString(), eq(NOW_AS_STORED));
        assertTrue(verified.isEmpty(), "a wrong token should be rejected");
        verify(tokens, description("possible theft should revoke every token of the account")).deleteAllForAccount(ACCOUNT_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-series", "aaaaaaaaaaaaaaaaaaaaa\u0000"})
    void malformedSeriesIsRejectedWithoutReachingTheDataStore(String series) {
        Optional<VerifiedRememberMeToken> verified = service().verify(series, TOKEN);

        assertTrue(verified.isEmpty(), "a malformed series is an unknown series");
        verify(tokens, never().description("a malformed series should not reach the data store")).find(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingTokenIsRejectedWithoutCountingAsTheft(String token) {
        Optional<VerifiedRememberMeToken> verified = service().verify(SERIES, token);

        assertTrue(verified.isEmpty(), "a request without a token should be rejected");
        verify(tokens, never().description("a request without a token should not reach the data store")).find(any());
        verify(tokens, never().description("a request without a token should not count as theft")).deleteAllForAccount(any());
    }

    @Test
    void revokeDeletesTheSeries() {
        service().revoke(SERIES);

        verify(tokens, description("the series should be deleted")).delete(SERIES);
    }

    @Test
    void revokeOfAMalformedSeriesHasNothingToDelete() {
        service().revoke("not-a-series");

        verify(tokens, never().description("a malformed series should not reach the data store")).delete(any());
    }
}
