package com.sunriseletting.identity.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the account unless one with the same email exists. The unique index makes this a single atomic
     * step, so concurrent requests for the same email can't both succeed.
     *
     * @return true if the account was stored, false if the email is already in use
     */
    public boolean insertIfEmailIsNew(NewAccount account) {
        int inserted = jdbc.sql("""
                        INSERT INTO accounts (id, email, title, title_other, first_name, surname, phone, password_hash, role, password_temporary)
                        VALUES (:id, :email, :title, :titleOther, :firstName, :surname, :phone, :passwordHash, :role, :passwordTemporary)
                        ON CONFLICT (email) DO NOTHING
                        """)
                .param("id", account.id())
                .param("email", account.email())
                .param("title", account.title())
                .param("titleOther", account.titleOther())
                .param("firstName", account.firstName())
                .param("surname", account.surname())
                .param("phone", account.phone())
                .param("passwordHash", account.passwordHash())
                .param("role", account.role().name())
                .param("passwordTemporary", account.passwordTemporary())
                .update();
        return inserted == 1;
    }

    public Optional<Role> findRoleById(UUID id) {
        return jdbc.sql("SELECT role FROM accounts WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> Role.valueOf(row.getString("role")))
                .optional();
    }

    /**
     * @param email the normalised email
     */
    public Optional<AccountCredentials> findCredentialsByEmail(String email) {
        // PostgreSQL text can't hold NUL and the driver throws if it is sent one, so no account can have this email.
        if (email.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id, password_hash, role, password_temporary FROM accounts WHERE email = :email")
                .param("email", email)
                .query(AccountRepository::credentials)
                .optional();
    }

    public Optional<AccountCredentials> findCredentialsById(UUID id) {
        return jdbc.sql("SELECT id, password_hash, role, password_temporary FROM accounts WHERE id = :id")
                .param("id", id)
                .query(AccountRepository::credentials)
                .optional();
    }

    /**
     * Stores the new hash and marks the password as no longer temporary, as one atomic step that only applies while the
     * password is still temporary, so of two concurrent changes only one can succeed.
     *
     * @return true if the password was replaced, false if the account has no temporary password
     */
    public boolean replaceTemporaryPassword(UUID id, String passwordHash) {
        int updated = jdbc.sql("""
                        UPDATE accounts SET password_hash = :passwordHash, password_temporary = false
                        WHERE id = :id AND password_temporary
                        """)
                .param("passwordHash", passwordHash)
                .param("id", id)
                .update();
        return updated == 1;
    }

    private static AccountCredentials credentials(ResultSet row, int rowNumber) throws SQLException {
        return new AccountCredentials(
                row.getObject("id", UUID.class),
                row.getString("password_hash"),
                Role.valueOf(row.getString("role")),
                row.getBoolean("password_temporary"));
    }
}
