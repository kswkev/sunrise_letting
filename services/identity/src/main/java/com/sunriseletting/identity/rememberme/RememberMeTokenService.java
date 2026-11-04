package com.sunriseletting.identity.rememberme;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sunriseletting.identity.account.AccountRepository;
import com.sunriseletting.identity.account.Role;

@Service
public class RememberMeTokenService {

    private static final Duration LIFETIME = Duration.ofDays(30);

    private final RememberMeTokenRepository tokens;
    private final AccountRepository accounts;
    private final Clock clock;

    public RememberMeTokenService(RememberMeTokenRepository tokens, AccountRepository accounts, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Starts a remember-me login for a Visitor account. No other account can have one: Property Managers must not
     * stay signed in after closing the browser.
     *
     * @return the new series and token, or empty if the account is not a Visitor account
     */
    public Optional<IssuedRememberMeToken> issue(UUID accountId) {
        boolean isVisitor = accountId != null && accounts.findRoleById(accountId).filter(Role.VISITOR::equals).isPresent();
        if (!isVisitor) {
            return Optional.empty();
        }

        String series = RememberMeTokenValues.newSeries();
        String token = RememberMeTokenValues.newToken();
        Instant expiresAt = now().plus(LIFETIME);
        tokens.insert(new NewRememberMeToken(series, RememberMeTokenValues.hash(token), accountId, expiresAt));
        return Optional.of(new IssuedRememberMeToken(series, token, expiresAt));
    }

    /**
     * Checks a token and swaps in a new one, so each token works once. The series keeps its expiry, so the login lasts
     * 30 days from signing in however often it is used. A known, unexpired series with any other token means someone
     * may have a stolen copy, so every token of the account is revoked.
     *
     * @return the account and the new token, or empty if the series and token were not accepted
     */
    public Optional<VerifiedRememberMeToken> verify(String series, String token) {
        if (!RememberMeTokenValues.isWellFormedSeries(series) || token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Instant now = now();
        Optional<StoredRememberMeToken> found = tokens.find(series).filter(stored -> now.isBefore(stored.expiresAt()));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        StoredRememberMeToken stored = found.get();
        String newToken = RememberMeTokenValues.newToken();
        boolean swapped = tokens.swapToken(series, RememberMeTokenValues.hash(token), RememberMeTokenValues.hash(newToken), now);
        if (!swapped) {
            tokens.deleteAllForAccount(stored.accountId());
            return Optional.empty();
        }
        return Optional.of(new VerifiedRememberMeToken(stored.accountId(), stored.role(), newToken, stored.expiresAt()));
    }

    public void revoke(String series) {
        if (RememberMeTokenValues.isWellFormedSeries(series)) {
            tokens.delete(series);
        }
    }

    /** To the microsecond, which is what PostgreSQL stores, so a time given out is the same as the time stored. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
