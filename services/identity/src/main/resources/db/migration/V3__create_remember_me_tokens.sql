-- Only the hash of a token is stored. The raw token exists only in the visitor's cookie.
CREATE TABLE remember_me_tokens (
    series       text        PRIMARY KEY,
    token_hash   text        NOT NULL,
    account_id   uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    expires_at   timestamptz NOT NULL,
    last_used_at timestamptz
);

-- A possibly stolen token revokes every token of its account.
CREATE INDEX remember_me_tokens_account_id_idx ON remember_me_tokens (account_id);
