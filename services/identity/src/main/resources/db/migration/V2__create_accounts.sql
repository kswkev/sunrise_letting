-- Lengths and formats are enforced by the account field rules, so they stay in one place.
CREATE TABLE accounts (
    id            uuid        PRIMARY KEY,
    email         text        NOT NULL,
    title         text        NOT NULL,
    title_other   text,
    first_name    text        NOT NULL,
    surname       text        NOT NULL,
    phone         text        NOT NULL,
    password_hash text        NOT NULL,
    role          text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Emails are stored normalised (trimmed, lower-cased), so this also catches concurrent duplicates.
CREATE UNIQUE INDEX accounts_email_key ON accounts (email);
