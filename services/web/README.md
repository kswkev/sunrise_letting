# web

## Purpose

The Sunrise Letting website. This service owns all pages, for both the public Visitor pages and the Property Manager area: page controllers, Thymeleaf templates and static assets. It uses HTMX for partial page updates, and its HTMX endpoints return Thymeleaf fragments.

It has no domain data store and holds no domain logic. Anything beyond rendering belongs in a domain service, which this service calls through its API.

HTTP sessions are kept in Redis, so the service can run as several replicas without sticky sessions. Redis here is session infrastructure only: it holds no domain data and no other service uses it.

## Build

Run from the repo root (use `./mvnw` on Linux or macOS):

```
mvnw.cmd verify -pl services/web -am
```

This compiles the service and runs its unit and integration tests. The integration tests need Docker running, because they start Redis and, for the real-browser tests of our own JavaScript (`*IT`, Chrome only), a browser with Testcontainers. `mvnw.cmd test` runs only the unit and page tests and doesn't need Docker.

## Deploy locally

You need JDK 17 and Docker. Maven comes with the wrapper at the repo root.

1. Start the service from the repo root:

   ```
   mvnw.cmd spring-boot:run -pl services/web
   ```

   This starts Redis from `compose.yaml` automatically and connects to it, with no manual setup.

2. Open <http://localhost:8080/> to see the home page.
3. Press `Ctrl+C` in the terminal to stop the service. Redis stops with it.

Pages that call identity, such as Create account, need the identity service running on its local address, <http://localhost:8081> (see its README). Pages that don't call it work without it. To point at a different address, set `identity.base-url`, or the environment variable `IDENTITY_BASEURL`.

The session cookie is `Secure`. Browsers accept that on `http://localhost`, but not on other plain HTTP addresses.

## Deploy to a cluster

There are no Kubernetes manifests yet. Whoever adds them needs to know that `web` needs a Redis reachable inside the cluster (a Deployment or StatefulSet plus a Service, or a managed Redis), with the connection passed as the environment variables `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` and `SPRING_DATA_REDIS_PASSWORD` (the password from a Secret). It also needs `IDENTITY_BASEURL` set to the identity Service's address. `web` can then run with more than one replica and no session affinity.
