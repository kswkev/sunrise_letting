# web

## Purpose

The Sunrise Letting website. This service owns all pages, for both the public Visitor pages and the Property Manager area: page controllers, Thymeleaf templates and static assets. It uses HTMX for partial page updates, and its HTMX endpoints return Thymeleaf fragments.

It has no domain data store and holds no domain logic. Anything beyond rendering belongs in a domain service, which this service calls through its API.

HTTP sessions are kept in Redis, so the service can run as several replicas without sticky sessions. Redis here is session infrastructure only: it holds no domain data and no other service uses it. Besides sessions it holds a short-lived gate (a hash of the remember-me series, no token) that lets only one request at a time exchange a remember-me cookie, across replicas. Remember-me tokens themselves live in identity; `web` only carries them in a cookie.

## Remember me

What a remembered Visitor gets, and the limits that were accepted when it was built. The token rules themselves are in identity's README.

- **The expiry is fixed at 30 days, not rolling.** It is set when the login is issued. Using the cookie replaces the token but doesn't extend the expiry, and the cookie's `Max-Age` comes from identity's `expiresAt`.
- **The session lasts as long as the login.** When a login is set at sign-in or exchanged, the session's idle time-out becomes what is left of the login, so it doesn't run out after 30 minutes and a form left open keeps working. Each request restarts the idle timer, so `RememberMeSessionFilter` sets the time-out again on each request, and the session ends with the login however active the Visitor is. There is no warning dialog on these sessions, including at that point. All other sessions keep their ordinary time-out.
- **A sign-in ends any login the browser already holds.** On a sign-in or a sign-up with no new login to remember, including a Property Manager who ticks the box, or when identity can't issue a token, the cookie is cleared and its series revoked. A new login replaces the cookie and revokes the old series. The earlier login may be someone else's on a shared browser, so it must not sign that browser in later.
- **Revoking a login doesn't end a remembered session that's already running.** That includes a session started from a stolen cookie. `web` doesn't check with identity again once a session exists, so such a session lasts until the login's fixed expiry, up to 30 days. The theft rule only stops further cookie exchanges.

**The exchange gate.** The cookie is only exchanged on full-page requests. `RedisRememberMeExchangeGate` also lets one request at a time through, across replicas, with `SET NX` on a SHA-256 of the series for 30 seconds. After a success the gate stays shut for the rest of those 30 seconds, so a request in flight with the old cookie never reaches identity and looks like theft. It reopens early if identity says the token is invalid or is unavailable. If Redis can't answer, the gate stays shut. A request that is turned away goes on as signed out, with the cookie untouched. So a second tab opened in that window, for example the second of two restored tabs, shows the signed-out header once, and the next full page is signed in as normal.

**What can still revoke a Visitor's logins.** Two lost responses can't be told apart from theft, so identity's theft rule can still revoke every login of the account. A browser with no session then has to sign in again, and one with a running session has to once that session ends:

- Identity replaces the token but `web` never gets the answer, for example on a read time-out. `web` treats that as unavailable and keeps the cookie, so the next attempt sends the replaced token.
- `web` gets the new token but the browser never gets the response, for example when the tab is closed mid-request. The browser keeps the old cookie, the gate holds it back for 30 seconds, and then it reaches identity.

**Signing out with identity down.** The cookie is cleared, so that browser is signed out, but the revoke is lost, so the series stays valid at identity until it expires. The same goes for clearing an earlier login at sign-in while identity is down.

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

2. Open <http://localhost:8080/> to see the home page, <http://localhost:8080/create-account> to sign up, or <http://localhost:8080/sign-in> to sign in.
3. Press `Ctrl+C` in the terminal to stop the service. Redis stops with it.

Pages that call identity, such as Create account and Sign in, need the identity service running on its local address, <http://localhost:8081> (see its README). Pages that don't call it work without it. To point at a different address, set `identity.base-url`, or the environment variable `IDENTITY_BASEURL`.

Property Managers sign in on the same page. To have an account locally, seed one in identity (see its README). Signing in with a permanent password goes to the Property Manager area at `/manage`. With a temporary password it goes to `/manage/change-password`, and every other `/manage` page redirects there until the password is changed.

The session, remember-me and signed-in marker cookies are `Secure`. Browsers accept that on `http://localhost`, but not on other plain HTTP addresses.

## Deploy to a cluster

There are no Kubernetes manifests yet. Whoever adds them needs to know that `web` needs a Redis reachable inside the cluster (a Deployment or StatefulSet plus a Service, or a managed Redis), with the connection passed as the environment variables `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` and `SPRING_DATA_REDIS_PASSWORD` (the password from a Secret). It also needs `IDENTITY_BASEURL` set to the identity Service's address. `web` can then run with more than one replica and no session affinity.

Deploy identity before `web` when upgrading to a `web` that reads `temporaryPassword`: `web` treats a sign-in answer without that field as identity being unavailable, so nobody can sign in until identity sends it.
