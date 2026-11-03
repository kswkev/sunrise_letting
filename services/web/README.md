# web

## Purpose

The Sunrise Letting website. This service owns all pages, for both the public Visitor pages and the Property Manager area: page controllers, Thymeleaf templates and static assets. It uses HTMX for partial page updates, and its HTMX endpoints return Thymeleaf fragments.

It has no data store and holds no domain logic. Anything beyond rendering belongs in a domain service, which this service calls through its API.

## Build

Run from the repo root (use `./mvnw` on Linux or macOS):

```
mvnw.cmd verify -pl services/web -am
```

This compiles the service and runs its unit and integration tests.

## Deploy locally

You need JDK 17. Maven comes with the wrapper at the repo root.

1. Start the service from the repo root:

   ```
   mvnw.cmd spring-boot:run -pl services/web
   ```

2. Open <http://localhost:8080/> to see the home page.
3. Press `Ctrl+C` in the terminal to stop the service.

The service doesn't call any domain services yet, so it needs nothing else running.
