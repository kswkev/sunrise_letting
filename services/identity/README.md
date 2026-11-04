# identity

## Purpose

Owns accounts and credentials for Sunrise Letting. It exposes JSON APIs only and never renders HTML; the `web` module calls it inside the cluster.

Its data store is PostgreSQL, with the schema managed by Flyway migrations in `src/main/resources/db/migration`. No other service reads that data store directly.

## API

All requests and responses are JSON. Field names are `title`, `titleOther`, `firstName`, `surname`, `email`, `phone`, `password` and `confirmPassword`. Errors are stable codes, which the caller maps to its own copy (see `AccountFieldError`).

| Request | Body | Responses |
|---|---|---|
| `POST /api/account-validation/{field}` | `{ "value": "...", "password": "..." }` (`password` only for `confirmPassword`) | `200 { "error": "<CODE>" }`, or `{ "error": null }` if valid. `404` for an unknown field. Never checks whether an account exists. |
| `POST /api/accounts/visitors` | All the fields above | `201 { "accountId": "<uuid>", "role": "VISITOR" }`. `400 { "errors": { "<field>": "<CODE>" } }` with one code per failing field. `409 { "error": "ACCOUNT_NOT_CREATED" }` if the email is in use. |
| `POST /api/authentications` | `{ "email": "...", "password": "..." }` | `200 { "accountId": "<uuid>", "role": "VISITOR" \| "PROPERTY_MANAGER", "temporaryPassword": true \| false }`. `400 { "errors": { "email": "EMAIL_REQUIRED", "password": "PASSWORD_REQUIRED" } }`, listing only the empty fields. `401 { "error": "INVALID_CREDENTIALS" }` for an unknown email or a wrong password alike. |
| `POST /api/accounts/{accountId}/password` | `{ "newPassword": "...", "confirmNewPassword": "..." }` | `204` with no body when the password is changed. `400 { "errors": { "newPassword": "<CODE>", "confirmNewPassword": "<CODE>" } }`, listing only the failing fields. `404 { "error": "ACCOUNT_NOT_FOUND" }` for an unknown account. `409 { "error": "NO_TEMPORARY_PASSWORD" }` if the account doesn't have a temporary password. `400` with no codes if `accountId` isn't a UUID or the body isn't valid JSON. |

The email is trimmed and matched without regard to case, as when an account is created. The password is never trimmed, so a password of spaces isn't empty; it is just wrong. An unknown email is checked against a dummy hash, so it takes as long to answer as a wrong password.

Changing a password is only for replacing a temporary password at first sign-in. Identity trusts the `accountId` in the path: the caller is `web`, which takes it from the signed-in session, and identity is only reachable inside the cluster.

- The account is checked first, so `404` and `409` are given whatever was submitted.
- `newPassword` gives the first that applies of `PASSWORD_REQUIRED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_MISSING_CHARACTER_TYPES` and `PASSWORD_SAME_AS_TEMPORARY`. The comparison with the temporary password comes last, because a temporary password doesn't have to meet the rules.
- `confirmNewPassword` gives `CONFIRM_PASSWORD_REQUIRED` or `PASSWORDS_DO_NOT_MATCH`.
- A change stores the new hash and clears the temporary flag in one step. Of two concurrent changes, one gets `204` and the other `409`.

Remember-me logins live here because identity owns credentials and `web` has no data store. `web` only holds the cookie.

| Request | Body | Responses |
|---|---|---|
| `POST /api/remember-me-tokens` | `{ "accountId": "<uuid>" }` | `201 { "series": "...", "token": "...", "expiresAt": "<ISO-8601 instant>" }` for a Visitor account. `422 { "error": "REMEMBER_ME_NOT_ALLOWED" }` for any other account, an unknown account or a missing `accountId`. `400` if `accountId` isn't a UUID. |
| `POST /api/remember-me-tokens/verify` | `{ "series": "...", "token": "..." }` | `200 { "accountId": "<uuid>", "role": "VISITOR", "token": "<new token>", "expiresAt": "<ISO-8601 instant>" }`. The token sent stops working. `401 { "error": "INVALID_REMEMBER_ME_TOKEN" }` in every other case. |
| `DELETE /api/remember-me-tokens/{series}` | None | `204`, also when the series is already gone. |

- A series and its token last 30 days from when they were issued. Using a token replaces it but doesn't extend the expiry.
- A known, unexpired series with any other token, including one already replaced, is treated as possible theft: every token of that account is revoked. An unknown or expired series, or a request with no series or token, revokes nothing.
- The series and token are URL-safe Base64 with no padding (22 and 43 characters).
- Only the SHA-256 hash of a token is stored, and token values are never logged.
- The expiry is fixed, not rolling: a Visitor who keeps coming back still has to sign in again 30 days after the login was issued.
- The theft rule can't tell a lost response from theft, so two cases can still revoke a Visitor's logins: identity replaces the token but `web` never gets the answer and sends the old one again, or `web` gets the new token but the browser doesn't and later sends the old one. See `web`'s README, which also covers how it keeps ordinary parallel requests from doing this.
- A revoke that never arrives changes nothing here: if `web` signs someone out while identity is down, the series stays valid until it expires, although that browser no longer holds the cookie.

Email, first name, surname and other title can't contain control characters once trimmed, because PostgreSQL text can't hold NUL. The email gets `EMAIL_INVALID`; the others get `FIRST_NAME_INVALID_CHARACTERS`, `SURNAME_INVALID_CHARACTERS` and `TITLE_OTHER_INVALID_CHARACTERS`.

A phone number can start with `0` or `+44` and must be in one of these ranges:

- mobiles: `07`, except `070` personal numbers and `076` pagers, but including `07624`
- landlines: `01` and `02`
- UK-wide numbers: `03`

Any other number gets `PHONE_INVALID`. That includes freephone `0800`, which is rejected on purpose.

Request bodies are never logged, because they carry passwords. Passwords are stored only as BCrypt hashes, pre-hashed with SHA-256 so that the whole of a long password counts (see `PreHashingBCryptPasswordEncoder`). Anything that checks a password must use that encoder.

## Build

Run from the repo root (use `./mvnw` on Linux or macOS), with Docker running:

```
mvnw.cmd verify -pl services/identity -am
```

This compiles the service and runs its unit and integration tests. The integration tests (`*IT`) start real PostgreSQL with Testcontainers, so they need Docker but not `compose.yaml`.

## Deploy locally

You need JDK 17 and Docker. Maven comes with the wrapper at the repo root.

1. Start Docker.
2. Start the service from the repo root:

   ```
   mvnw.cmd spring-boot:run -pl services/identity
   ```

   Spring Boot starts PostgreSQL from `services/identity/compose.yaml`, connects to it, applies the Flyway migrations and listens on <http://localhost:8081/>.
3. Press `Ctrl+C` in the terminal to stop the service. Spring Boot stops the PostgreSQL container too.

No Property Manager accounts are seeded unless you ask for them. To have one locally, set these in the same terminal before step 2, with values of your own:

```
$env:IDENTITY_PROPERTY_MANAGERS_0_EMAIL = "pm@example.com"
$env:IDENTITY_PROPERTY_MANAGERS_0_TEMPORARY_PASSWORD = "<a temporary password>"
```

## Configuration

`compose.yaml` is for local runs only. Everywhere else, set the datasource with environment variables:

| Variable | Meaning |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL of the PostgreSQL database |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `IDENTITY_PROPERTY_MANAGERS_<n>_EMAIL` | Email of a Property Manager account to seed. Optional, supplied by the deployment secret. |
| `IDENTITY_PROPERTY_MANAGERS_<n>_TEMPORARY_PASSWORD` | That account's temporary password. It doesn't have to meet the sign-up password rules. |

`<n>` counts up from 0 with no gaps; entries after a gap are ignored.

### Seeding Property Manager accounts

On each start, the service goes through the configured accounts:

- If no account has the email (trimmed and lower-cased), it creates one with the Property Manager role only and the password marked as temporary. The password is stored only as a hash, made by the same encoder as every other password, so it signs in through `POST /api/authentications`.
- If the email belongs to a Property Manager, it leaves that account unchanged, so a restart creates no duplicates and doesn't undo a changed password. Removing an entry doesn't remove the account.
- If the email belongs to a Visitor, it leaves that account unchanged, logs an error giving the entry's number but not the email, and carries on, because one account can't hold both roles. That person needs a different email for their Property Manager account.
- If an entry has no email or no password, it logs an error giving the entry's number and carries on.

Finally it logs how many accounts were created and skipped. Emails and temporary passwords are never logged.
