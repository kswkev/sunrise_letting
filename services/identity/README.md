# identity

## Purpose

Owns accounts and credentials for Sunrise Letting. It exposes JSON APIs only and never renders HTML; the `web` service calls it inside the cluster.

Its data store is PostgreSQL, with the schema managed by Flyway migrations in `src/main/resources/db/migration`. No other service reads that data store directly.

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
