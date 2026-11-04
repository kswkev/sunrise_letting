package com.sunriseletting.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.testsupport.CookieJarClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

/** Sign out over real HTTP, with sessions in real Redis and identity mocked. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SignOutIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    @LocalServerPort
    private int port;

    @MockitoBean
    private IdentityClient identityClient;

    @Autowired
    private RedisConnectionFactory redis;

    private CookieJarClient browser;

    @BeforeEach
    void signIn() throws Exception {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        browser = new CookieJarClient(port);
        HttpResponse<String> form = browser.get("/sign-in");
        HttpResponse<String> signedIn = browser.post("/sign-in",
                Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com", "password", SECRET));
        assertEquals(303, signedIn.statusCode(), "the Visitor should be signed in");
    }

    @Test
    void signingOutDeletesTheSessionClearsItsCookieAndShowsTheSignedOutHeader() throws Exception {
        String sessionCookie = browser.cookie("SESSION");
        assertTrue(keyExists(sessionCookie), "the session should be in Redis before signing out");
        HttpResponse<String> page = browser.get("/properties");
        assertFalse(Jsoup.parse(page.body()).select("form.sign-out input[name=_csrf]").isEmpty(), "the header should have the sign-out form");

        HttpResponse<String> signedOut = browser.post("/sign-out", Map.of("_csrf", CookieJarClient.csrfToken(page)));

        assertEquals(303, signedOut.statusCode(), "sign out should answer See Other");
        assertEquals("/properties", signedOut.headers().firstValue("Location").orElse(null), "sign out lands on the search page");
        assertFalse(keyExists(sessionCookie), "the session should be gone from Redis");
        assertTrue(CookieJarClient.setCookieHeader(signedOut, "SESSION").contains("Max-Age=0"), "the session cookie should be cleared");

        Document next = Jsoup.parse(browser.get("/properties").body());
        assertEquals(1, next.select("header a[href=/sign-in]").size(), "the next page shows Sign in");
        assertEquals(1, next.select("header a[href=/create-account]").size(), "the next page shows Create account");
        assertEquals(0, next.select("form.sign-out").size(), "the next page has no Sign out");
        assertEquals(0, next.select(".notification-banner").size(), "there is no message after signing out");
    }

    @Test
    void signingOutWithAStaleTokenAfterTheSessionEndedStillLandsOnProperties() throws Exception {
        HttpResponse<String> page = browser.get("/properties");
        String staleToken = CookieJarClient.csrfToken(page);
        deleteSession(browser.cookie("SESSION"));

        HttpResponse<String> signedOut = browser.post("/sign-out", Map.of("_csrf", staleToken));

        assertEquals(303, signedOut.statusCode(), "there should be no 403 page");
        assertEquals("/properties", signedOut.headers().firstValue("Location").orElse(null), "sign out lands on the search page");
        assertNull(browser.cookie("SESSION"), "no session cookie should be left behind");
        assertEquals(0, Jsoup.parse(browser.get("/properties").body()).select("form.sign-out").size(), "the header should be signed out");
    }

    @Test
    void headerIsSignedInStraightAfterSigningIn() throws Exception {
        Document page = Jsoup.parse(browser.get("/").body());

        assertNotNull(page.selectFirst("form.sign-out button.sign-out__button"), "the header should offer Sign out");
        assertEquals(0, page.select("header a[href=/sign-in]").size(), "Sign in isn't rendered");
    }

    private boolean keyExists(String cookie) {
        try (RedisConnection connection = redis.getConnection()) {
            return Boolean.TRUE.equals(connection.keyCommands().exists(key(cookie)));
        }
    }

    private void deleteSession(String cookie) {
        try (RedisConnection connection = redis.getConnection()) {
            connection.keyCommands().del(key(cookie));
        }
    }

    private byte[] key(String cookie) {
        String id = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
        return ("sunrise:web:sessions:sessions:" + id).getBytes(StandardCharsets.UTF_8);
    }
}
