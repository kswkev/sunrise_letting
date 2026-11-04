package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Remember me over real HTTP, with sessions and the exchange gate in real Redis, and identity mocked. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RememberMeIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String TOKEN = "T".repeat(43);

    private static final String NEW_TOKEN = "N".repeat(43);

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
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
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
