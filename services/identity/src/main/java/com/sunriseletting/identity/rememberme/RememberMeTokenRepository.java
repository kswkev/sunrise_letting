package com.sunriseletting.identity.rememberme;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.sunriseletting.identity.account.Role;

@Repository
public class RememberMeTokenRepository {

    private final JdbcClient jdbc;

    public RememberMeTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(NewRememberMeToken token) {
        jdbc.sql("""
                        INSERT INTO remember_me_tokens (series, token_hash, account_id, expires_at)
                        VALUES (:series, :tokenHash, :accountId, :expiresAt)
                        """)
                .param("series", token.series())
                .param("tokenHash", token.tokenHash())
                .param("accountId", token.accountId())
                .param("expiresAt", toDatabaseTime(token.expiresAt()))
                .update();
    }

    public Optional<StoredRememberMeToken> find(String series) {
        return jdbc.sql("""
                        SELECT t.series, t.account_id, a.role, t.expires_at
                        FROM remember_me_tokens t
                        JOIN accounts a ON a.id = t.account_id
                        WHERE t.series = :series
                        """)
                .param("series", series)
                .query((row, rowNumber) -> new StoredRememberMeToken(
                        row.getString("series"),
                        row.getObject("account_id", UUID.class),
                        Role.valueOf(row.getString("role")),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    /**
     * Replaces the token if the one presented is still the current one. This is a single atomic step, so when one token
     * is used twice at the same moment only one use can succeed.
     *
     * @return true if the token was replaced, false if the one presented is not the current one
     */
    public boolean swapToken(String series, String currentHash, String newHash, Instant usedAt) {
        int updated = jdbc.sql("""
                        UPDATE remember_me_tokens
                        SET token_hash = :newHash, last_used_at = :usedAt
                        WHERE series = :series AND token_hash = :currentHash
                        """)
                .param("newHash", newHash)
                .param("usedAt", toDatabaseTime(usedAt))
                .param("series", series)
                .param("currentHash", currentHash)
                .update();
        return updated == 1;
    }

    public void delete(String series) {
        jdbc.sql("DELETE FROM remember_me_tokens WHERE series = :series").param("series", series).update();
    }

    public void deleteAllForAccount(UUID accountId) {
        jdbc.sql("DELETE FROM remember_me_tokens WHERE account_id = :accountId").param("accountId", accountId).update();
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
