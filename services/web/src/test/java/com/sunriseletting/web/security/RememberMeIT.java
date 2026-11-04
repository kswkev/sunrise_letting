package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.identity.RememberMeTokenResult;
import com.sunriseletting.web.identity.RememberMeVerifyResult;
import com.sunriseletting.web.testsupport.CookieJarClient;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Remember me over real HTTP, with sessions and the exchange gate in real Redis, and identity mocked. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, RememberMeIT.FixtureController.class })
class RememberMeIT {

    /** Stands in for a page's form or HTMX action, which needs a live session and a good CSRF token. */
    @RestController
    static class FixtureController {

        @PostMapping("/fixture/form")
        String form(@AuthenticationPrincipal SessionPrincipal principal) {
            return principal == null ? "nobody" : principal.accountId();
        }
    }
    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String TOKEN = "T".repeat(43);

    private static final String NEW_TOKEN = "N".repeat(43);

    private static final String SESSION_KEY_PREFIX = "sunrise:web:sessions:sessions:";

    @LocalServerPort
    private int port;

    @MockitoBean
    private IdentityClient identityClient;

    @Autowired
    private RememberMeExchangeGate gate;

    @Autowired
    private RedisConnectionFactory redis;

    /** A new series for each test, so the gate from one test doesn't shut out the next. */
    private String series;

    private CookieJarClient browser;

    @BeforeEach
    void setUp() {
        reset(identityClient);
        series = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        browser = new CookieJarClient(port);
    }

    @Test
    void afterClosingTheBrowserTheVisitorIsSignedBackInAndTheFlagSurvivesRedis() throws Exception {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(series, TOKEN, Instant.now().plus(Duration.ofDays(30))));
        when(identityClient.verifyRememberMeToken(series, TOKEN)).thenReturn(verified());
        HttpResponse<String> form = browser.get("/sign-in");
        browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com", "password", SECRET, "remember-me", "on"));
        assertEquals(series + "." + TOKEN, browser.cookie("remember-me"), "signing in should set the cookie");
        assertEquals("true", sessionAttribute(browser.cookie("SESSION"), RememberMeLogin.SESSION_ATTRIBUTE), "the flag should be stored in Redis");
        assertEquals(1, Jsoup.parse(browser.get("/properties").body()).select("form.sign-out").size(), "the flag should read back from Redis");

        browser.removeCookie("SESSION");
        HttpResponse<String> back = browser.get("/properties");

        assertEquals(200, back.statusCode(), "no redirect, no message");
        assertEquals(1, Jsoup.parse(back.body()).select("form.sign-out").size(), "the Visitor should be signed in");
        assertEquals(series + "." + NEW_TOKEN, browser.cookie("remember-me"), "the cookie should hold the new token");
        assertEquals(1, Jsoup.parse(browser.get("/properties").body()).select("form.sign-out").size(), "the next request uses the new session");
        verify(identityClient, times(1).description("the cookie should be verified once")).verifyRememberMeToken(series, TOKEN);
        verify(identityClient, description("the token should be issued once")).issueRememberMeToken("account-1");
        verify(identityClient, description("the details should be checked once")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void parallelRequestsWithTheSameCookieReachIdentityOnce() throws Exception {
        CountDownLatch exchanging = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(identityClient.verifyRememberMeToken(series, TOKEN)).thenAnswer(invocation -> {
            exchanging.countDown();
            release.await(10, TimeUnit.SECONDS);
            return verified();
        });

        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() -> getProperties(new CookieJarClient(port)));
        assertTrue(exchanging.await(10, TimeUnit.SECONDS), "the first request should reach identity");
        List<HttpResponse<String>> others = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            others.add(getProperties(new CookieJarClient(port)));
        }
        release.countDown();

        assertEquals(1, Jsoup.parse(first.get(10, TimeUnit.SECONDS).body()).select("form.sign-out").size(), "the first request is signed in");
        for (HttpResponse<String> other : others) {
            assertEquals(200, other.statusCode(), "a parallel request should still get its page");
            assertEquals(1, Jsoup.parse(other.body()).select("header a[href=/sign-in]").size(), "a parallel request goes on as signed out");
            assertEquals(null, CookieJarClient.setCookieHeader(other, "remember-me"), "a parallel request leaves the cookie alone");
        }
        verify(identityClient, times(1).description("the token must be spent only once, or identity treats it as stolen")).verifyRememberMeToken(any(), any());
    }

    @Test
    void theOldCookieArrivingJustAfterTheExchangeIsNotSentToIdentity() throws Exception {
        when(identityClient.verifyRememberMeToken(series, TOKEN)).thenReturn(verified());
        browser.setCookie("remember-me", series + "." + TOKEN);
        browser.get("/properties");

        CookieJarClient slowerTab = new CookieJarClient(port);
        slowerTab.setCookie("remember-me", series + "." + TOKEN);
        HttpResponse<String> late = slowerTab.get("/properties");

        assertEquals(200, late.statusCode(), "the page should load");
        assertEquals(1, Jsoup.parse(late.body()).select("header a[href=/sign-in]").size(), "this request isn't signed in");
        assertEquals(series + "." + TOKEN, slowerTab.cookie("remember-me"), "the cookie is left alone, as the browser now holds the new one");
        verify(identityClient, times(1).description("a request in flight must not look like theft")).verifyRememberMeToken(any(), any());
    }

    @Test
    void gateLetsOneThroughUntilItIsReopened() {
        assertTrue(gate.tryEnter(series), "the first request gets through");
        assertFalse(gate.tryEnter(series), "the second is turned away");

        gate.reopen(series);

        assertTrue(gate.tryEnter(series), "after reopening, the next request gets through");
    }

    @Test
    void gateStoresNoSeriesOrTokenAndExpires() {
        gate.tryEnter(series);

        try (RedisConnection connection = redis.getConnection()) {
            List<String> keys = new ArrayList<>();
            connection.keyCommands().keys("sunrise:web:remember-me-exchange:*".getBytes(StandardCharsets.UTF_8))
                    .forEach(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            assertFalse(keys.isEmpty(), "the gate should be in Redis");
            assertTrue(keys.stream().noneMatch(key -> key.contains(series)), "the series itself isn't stored");
            for (String key : keys) {
                Long ttl = connection.keyCommands().ttl(key.getBytes(StandardCharsets.UTF_8));
                assertNotNull(ttl, "the gate should have a time to live");
                assertTrue(ttl > 0 && ttl <= 30, "the gate should shut for a short while only, but was " + ttl);
            }
        }
    }

    @Test
    void signingInWithTheBoxKeepsTheSessionInRedisForTheLifeOfTheLogin() throws Exception {
        signInRemembered(Instant.now().plus(Duration.ofDays(30)));

        String cookie = browser.cookie("SESSION");
        assertLastsAbout(Duration.ofDays(30), storedInterval(cookie), "the stored time-out should match the login's remaining life");
        assertLastsAbout(Duration.ofDays(30), timeToLive(cookie), "Redis should keep the session that long too");
    }

    @Test
    void exchangingTheCookieKeepsTheSessionInRedisForWhatIsLeftOfTheLogin() throws Exception {
        when(identityClient.verifyRememberMeToken(series, TOKEN)).thenReturn(verified());
        browser.setCookie("remember-me", series + "." + TOKEN);

        HttpResponse<String> page = browser.get("/properties");

        assertEquals(200, page.statusCode(), "the page should load");
        String cookie = browser.cookie("SESSION");
        assertLastsAbout(Duration.ofDays(29), storedInterval(cookie), "the stored time-out should match what is left of the login");
        assertLastsAbout(Duration.ofDays(29), timeToLive(cookie), "Redis should keep the session that long too");
        verify(identityClient, description("the cookie should be verified")).verifyRememberMeToken(series, TOKEN);
    }

    @Test
    void anOrdinarySessionStaysAtThirtyMinutesInRedis() throws Exception {
        signInWithoutTheBox();

        String cookie = browser.cookie("SESSION");
        assertEquals(1800, storedInterval(cookie), "other sessions don't get longer");
        assertTrue(timeToLive(cookie) <= 1800, "Redis should drop the session after 30 minutes");
    }

    @Test
    void aFormPostAfterAThirtyMinuteIdleStillWorksOnARememberedSession() throws Exception {
        signInRemembered(Instant.now().plus(Duration.ofDays(30)));
        String token = CookieJarClient.csrfToken(browser.get("/properties"));
        idleForThirtyOneMinutes();

        HttpResponse<String> posted = browser.post("/fixture/form", Map.of("_csrf", token, "message", "typed"));

        assertEquals(200, posted.statusCode(), "the form shouldn't be sent to sign-in, losing what was typed");
        assertEquals("account-1", posted.body(), "the Visitor should still be signed in");
    }

    @Test
    void anHtmxPostAfterAThirtyMinuteIdleStillWorksOnARememberedSession() throws Exception {
        signInRemembered(Instant.now().plus(Duration.ofDays(30)));
        String token = CookieJarClient.csrfToken(browser.get("/properties"));
        idleForThirtyOneMinutes();

        HttpResponse<String> posted = browser.post("/fixture/form", Map.of("_csrf", token), "HX-Request", "true", "HX-Current-URL", "http://localhost/properties");

        assertEquals(200, posted.statusCode(), "the action should go through");
        assertEquals("account-1", posted.body(), "the Visitor should still be signed in");
        assertNull(posted.headers().firstValue("HX-Redirect").orElse(null), "no redirect to sign-in");
    }

    @Test
    void aFormPostAfterAThirtyMinuteIdleOnAnOrdinarySessionGoesToSignIn() throws Exception {
        signInWithoutTheBox();
        String token = CookieJarClient.csrfToken(browser.get("/properties"));
        idleForThirtyOneMinutes();

        HttpResponse<String> posted = browser.post("/fixture/form", Map.of("_csrf", token));

        assertEquals(302, posted.statusCode(), "an ordinary session still idles out");
        assertEquals("/sign-in?timedOut=true&returnTo=%2Ffixture%2Fform", posted.headers().firstValue("Location").orElse(null), "with the inactivity message");
    }

    @Test
    void activityDoesNotCarryARememberedSessionPastTheEndOfTheLogin() throws Exception {
        signInRemembered(Instant.now().plus(Duration.ofDays(30)));
        String cookie = browser.cookie("SESSION");
        setStoredExpiry(cookie, Instant.now().plus(Duration.ofHours(1)));

        HttpResponse<String> page = browser.get("/properties");

        assertEquals(200, page.statusCode(), "the page should load");
        assertLastsAbout(Duration.ofHours(1), storedInterval(cookie), "the stored time-out should shrink as the login runs out");
        assertLastsAbout(Duration.ofHours(1), timeToLive(cookie), "Redis should drop the session when the login ends");
    }

    private void signInRemembered(Instant expiresAt) throws Exception {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(series, TOKEN, expiresAt));
        HttpResponse<String> form = browser.get("/sign-in");
        HttpResponse<String> signedIn = browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com", "password", SECRET, "remember-me", "on"));
        assertEquals(303, signedIn.statusCode(), "the Visitor should be signed in");
        verify(identityClient, description("the token should be issued")).issueRememberMeToken("account-1");
        verify(identityClient, description("the details should be checked")).authenticate("jo@example.com", SECRET);
    }

    private void signInWithoutTheBox() throws Exception {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR));
        HttpResponse<String> form = browser.get("/sign-in");
        HttpResponse<String> signedIn = browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com", "password", SECRET));
        assertEquals(303, signedIn.statusCode(), "the Visitor should be signed in");
        verify(identityClient, description("the details should be checked")).authenticate("jo@example.com", SECRET);
    }

    /** Moves the session's last access back, which is the moment Spring Session measures the idle time from. */
    private void idleForThirtyOneMinutes() {
        long lastAccessed = Instant.now().minus(Duration.ofMinutes(31)).toEpochMilli();
        try (RedisConnection connection = redis.getConnection()) {
            connection.hashCommands().hSet(sessionKey(browser.cookie("SESSION")), "lastAccessedTime".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(lastAccessed).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void setStoredExpiry(String cookie, Instant expiresAt) {
        try (RedisConnection connection = redis.getConnection()) {
            connection.hashCommands().hSet(sessionKey(cookie), ("sessionAttr:" + RememberMeLogin.EXPIRES_AT_ATTRIBUTE).getBytes(StandardCharsets.UTF_8),
                    ("\"" + expiresAt + "\"").getBytes(StandardCharsets.UTF_8));
        }
    }

    private long storedInterval(String cookie) {
        try (RedisConnection connection = redis.getConnection()) {
            byte[] raw = connection.hashCommands().hGet(sessionKey(cookie), "maxInactiveInterval".getBytes(StandardCharsets.UTF_8));
            return Long.parseLong(new String(raw, StandardCharsets.UTF_8));
        }
    }

    private long timeToLive(String cookie) {
        try (RedisConnection connection = redis.getConnection()) {
            return connection.keyCommands().ttl(sessionKey(cookie));
        }
    }

    private byte[] sessionKey(String cookie) {
        return (SESSION_KEY_PREFIX + new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    private void assertLastsAbout(Duration expected, long seconds, String message) {
        assertTrue(Math.abs(expected.toSeconds() - seconds) < 60, message + ": expected about " + expected.toSeconds() + " but was " + seconds);
    }

    private HttpResponse<String> getProperties(CookieJarClient client) {
        client.setCookie("remember-me", series + "." + TOKEN);
        try {
            return client.get("/properties");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RememberMeVerifyResult verified() {
        return new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, NEW_TOKEN, Instant.now().plus(Duration.ofDays(29)));
    }

    private String sessionAttribute(String cookie, String attribute) {
        String id = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
        try (RedisConnection connection = redis.getConnection()) {
            byte[] raw = connection.hashCommands().hGet(("sunrise:web:sessions:sessions:" + id).getBytes(StandardCharsets.UTF_8),
                    ("sessionAttr:" + attribute).getBytes(StandardCharsets.UTF_8));
            return raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        }
    }
}
