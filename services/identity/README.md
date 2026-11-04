# identity

## Purpose

Owns accounts and credentials for Sunrise Letting. It exposes JSON APIs only and never renders HTML; the `web` service calls it inside the cluster.

Its data store is PostgreSQL, with the schema managed by Flyway migrations in `src/main/resources/db/migration`. No other service reads that data store directly.

## API

All requests and responses are JSON. Field names are `title`, `titleOther`, `firstName`, `surname`, `email`, `phone`, `password` and `confirmPassword`. Errors are stable codes, which the caller maps to its own copy (see `AccountFieldError`).

| Request | Body | Responses |
|---|---|---|
| `POST /api/account-validation/{field}` | `{ "value": "...", "password": "..." }` (`password` only for `confirmPassword`) | `200 { "error": "<CODE>" }`, or `{ "error": null }` if valid. `404` for an unknown field. Never checks whether an account exists. |
| `POST /api/accounts/visitors` | All the fields above | `201 { "accountId": "<uuid>", "role": "VISITOR" }`. `400 { "errors": { "<field>": "<CODE>" } }` with one code per failing field. `409 { "error": "ACCOUNT_NOT_CREATED" }` if the email is in use. |

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

## Configuration

`compose.yaml` is for local runs only. Everywhere else, set the datasource with environment variables:

| Variable | Meaning |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL of the PostgreSQL database |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
