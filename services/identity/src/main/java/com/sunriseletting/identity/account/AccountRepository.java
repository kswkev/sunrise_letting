package com.sunriseletting.identity.account;

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
                        INSERT INTO accounts (id, email, title, title_other, first_name, surname, phone, password_hash, role)
                        VALUES (:id, :email, :title, :titleOther, :firstName, :surname, :phone, :passwordHash, :role)
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
                .update();
        return inserted == 1;
    }
}
