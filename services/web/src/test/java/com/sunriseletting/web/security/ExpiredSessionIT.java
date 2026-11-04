package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.identity.RememberMeVerifyResult;
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

/** What happens after a session runs out, over real HTTP. A session is expired by deleting it from real Redis. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExpiredSessionIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    @LocalServerPort
    private int port;

    @MockitoBean
    private IdentityClient identityClient;

    @Autowired
    private RedisConnectionFactory redis;

    private CookieJarClient browser;

    @BeforeEach
    void setUp() {
        reset(identityClient);
        browser = new CookieJarClient(port);
    }

    @Test
    void signingInSetsTheMarkerAndSigningOutClearsIt() throws Exception {
        HttpResponse<String> signedIn = signIn();
        String setCookie = CookieJarClient.setCookieHeader(signedIn, "signed-in");
        assertTrue(setCookie.startsWith("signed-in=1;"), "the marker is a constant");
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("Secure") && setCookie.contains("SameSite=Lax") && setCookie.contains("Path=/"),
                "the marker has the agreed attributes: " + setCookie);
        assertFalse(setCookie.contains("Max-Age"), "the marker ends with the browser");

        HttpResponse<String> page = browser.get("/properties");
        HttpResponse<String> signedOut = browser.post("/sign-out", Map.of("_csrf", CookieJarClient.csrfToken(page)));

        assertTrue(CookieJarClient.setCookieHeader(signedOut, "signed-in").contains("Max-Age=0"), "sign out clears the marker");
    }

    @Test
    void signingUpSetsTheMarker() throws Exception {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        HttpResponse<String> form = browser.get("/create-account");

        HttpResponse<String> created = browser.post("/create-account", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com"));

        assertEquals(303, created.statusCode(), "the Visitor should be signed up");
        assertEquals("1", browser.cookie("signed-in"), "sign-up signs in, so it sets the marker too");
        verify(identityClient, description("identity should be asked")).createVisitor(any());
    }

    @Test
    void expiredGetGoesToTheTimedOutAddressOnceAndThenWithoutTheMessage() throws Exception {
        signIn();
        expireSession();

        HttpResponse<String> first = browser.get("/manage/x?a=1");

        assertEquals(302, first.statusCode(), "an expired session is redirected");
        assertEquals("/sign-in?timedOut=true&returnTo=%2Fmanage%2Fx%3Fa%3D1", location(first), "with the message and the return address");
        assertTrue(CookieJarClient.setCookieHeader(first, "signed-in").contains("Max-Age=0"), "the redirect clears the marker");

        HttpResponse<String> second = browser.get("/manage/x");

        assertEquals("/sign-in?returnTo=%2Fmanage%2Fx", location(second), "the message shows once");
    }

    @Test
    void expiredPostWithAStaleTokenGoesToTheTimedOutAddressNotA403() throws Exception {
        signIn();
        String staleToken = CookieJarClient.csrfToken(browser.get("/properties"));
        expireSession();

        HttpResponse<String> posted = browser.post("/properties/123/viewing", Map.of("_csrf", staleToken, "message", "something-typed-in-the-form"));

        assertEquals(302, posted.statusCode(), "not a 403");
        assertEquals("/sign-in?timedOut=true&returnTo=%2Fproperties%2F123%2Fviewing", location(posted), "back to the form's own page, carrying nothing that was typed");
        assertTrue(CookieJarClient.setCookieHeader(posted, "signed-in").contains("Max-Age=0"), "the redirect clears the marker");
        HttpResponse<String> signInPage = browser.get(location(posted));
        assertEquals(200, signInPage.statusCode(), "the sign-in page should show");
        assertFalse(signInPage.body().contains("something-typed-in-the-form"), "what was typed isn't kept");
    }

    @Test
    void expiredHtmxRequestGetsAnHxRedirectForTheCurrentPage() throws Exception {
        signIn();
        expireSession();

        HttpResponse<String> response = browser.get("/manage/fragment", "HX-Request", "true", "HX-Current-URL", "http://localhost/properties?q=leeds");

        assertEquals("/sign-in?timedOut=true&returnTo=%2Fproperties%3Fq%3Dleeds", response.headers().firstValue("HX-Redirect").orElse(null),
                "the return address is the page, not the endpoint");
        assertNull(response.headers().firstValue("Location").orElse(null), "no real redirect");
        assertTrue(CookieJarClient.setCookieHeader(response, "signed-in").contains("Max-Age=0"), "the response clears the marker");
    }

    @Test
    void afterAnExpiredPageSigningInReturnsToItAndTheBannerShows() throws Exception {
        signIn();
        expireSession();
        String address = location(browser.get("/manage/x"));

        HttpResponse<String> signInPage = browser.get(address);

        Document page = Jsoup.parse(signInPage.body());
        assertEquals("You were signed out because you were inactive", page.select(".notification-banner--info h2").text(), "the page says why");
        HttpResponse<String> signedIn = browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(signInPage), "email", "jo@example.com",
                "password", SECRET, "returnTo", page.select("input[name=returnTo]").attr("value"), "timedOut", "true"));
        assertEquals("/manage/x", location(signedIn), "after signing in the Visitor goes back");
    }

    @Test
    void timedOutAddressClearsTheMarkerWhenTheScriptNavigatedThereItself() throws Exception {
        signIn();
        expireSession();

        HttpResponse<String> page = browser.get("/sign-in?timedOut=true&returnTo=%2Fproperties");

        assertEquals(200, page.statusCode(), "the sign-in page should show");
        assertTrue(CookieJarClient.setCookieHeader(page, "signed-in").contains("Max-Age=0"), "the marker goes when the message shows");
        assertEquals("/sign-in?returnTo=%2Fmanage", location(browser.get("/manage")), "after that the browser counts as never signed in");
    }

    @Test
    void timedOutAddressWithALiveSessionGoesBackAndLeavesTheMarker() throws Exception {
        signIn();

        HttpResponse<String> response = browser.get("/sign-in?timedOut=true&returnTo=%2Fproperties%2F123");

        assertEquals(302, response.statusCode(), "the tab goes straight back");
        assertEquals("/properties/123", URI.create(location(response)).getPath(), "to its return address");
        assertNull(CookieJarClient.setCookieHeader(response, "signed-in"), "the marker stays, so a real time-out still shows the message");
    }

    @Test
    void aVisitorIsDeniedTheManagerAreaAndStaysSignedIn() throws Exception {
        signIn();

        HttpResponse<String> denied = browser.get("/manage");

        assertEquals(403, denied.statusCode(), "a Visitor is denied");
        assertEquals("You do not have permission to view this page", Jsoup.parse(denied.body()).select("main h1").text(), "the full page");
        HttpResponse<String> next = browser.get("/properties");
        assertEquals(1, Jsoup.parse(next.body()).select("header form.sign-out").size(), "the session is unchanged");
        HttpResponse<String> signedOut = browser.post("/sign-out", Map.of("_csrf", CookieJarClient.csrfToken(denied)));
        assertNull(browser.cookie("SESSION"), "the sign-out button on the access-denied page works");
        assertEquals(303, signedOut.statusCode(), "the token rendered on the access-denied page is good");
    }

    @Test
    void keepAliveResetsTheIdleTimerAndAnswers401OnceTheSessionHasEnded() throws Exception {
        signIn();
        HttpResponse<String> page = browser.get("/properties");
        Document document = Jsoup.parse(page.body());
        assertEquals("1800", document.select("#timeout-dialog").attr("data-timeout-seconds"), "a real session's own time-out reaches the page");
        String token = document.select("#timeout-dialog form input[name=_csrf]").attr("value");
        long before = lastAccessed();
        Thread.sleep(20);

        HttpResponse<String> alive = browser.post("/session/keep-alive", Map.of("_csrf", token));

        assertEquals(204, alive.statusCode(), "the session should be kept alive");
        assertTrue(lastAccessed() > before, "the idle timer should restart");
        expireSession();

        HttpResponse<String> ended = browser.post("/session/keep-alive", Map.of("_csrf", token));

        assertEquals(401, ended.statusCode(), "a stale token on an ended session is a 401");
        assertEquals("", ended.body(), "no body");
        assertNull(location(ended), "no redirect");
        assertNull(ended.headers().firstValue("HX-Redirect").orElse(null), "no HX-Redirect");
    }

    @Test
    void noSessionAndNoMarkerIsNeverSignedIn() throws Exception {
        assertEquals("/sign-in?returnTo=%2Fmanage", location(browser.get("/manage")), "no inactivity message");
    }

    @Test
    void anExpiredFormOnSignInWithTheMarkerIsTheExpiredFormNotARedirect() throws Exception {
        browser.setCookie("signed-in", "1");

        HttpResponse<String> response = browser.post("/sign-in", Map.of("_csrf", "stale", "email", "jo@example.com", "password", SECRET));

        assertEquals(200, response.statusCode(), "the sign-in form is a signed-out form");
        assertTrue(response.body().contains("Your form has expired"), "the page should say so");
        assertNull(CookieJarClient.setCookieHeader(response, "signed-in"), "the marker isn't touched");
        verify(identityClient, never().description("the details aren't checked")).authenticate(any(), any());
    }

    @Test
    void aRememberMeCookieIsHandledFirstSoThereIsNoRedirectToSignIn() throws Exception {
        String series = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        String token = "T".repeat(43);
        when(identityClient.verifyRememberMeToken(series, token)).thenReturn(
                new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, "N".repeat(43), Instant.now().plus(Duration.ofDays(1))));
        browser.setCookie("signed-in", "1");
        browser.setCookie("remember-me", series + "." + token);

        HttpResponse<String> response = browser.get("/manage/x");

        assertEquals(403, response.statusCode(), "the Visitor should be signed back in silently, so a Visitor's answer on this path, with no redirect");
        assertEquals("You do not have permission to view this page", Jsoup.parse(response.body()).select("main h1").text(), "the signed-in Visitor's access-denied page");
        String marker = CookieJarClient.setCookieHeader(response, "signed-in");
        assertNotNull(marker, "the silent sign-in should set the marker again");
        assertTrue(marker.startsWith("signed-in=1;"), "the silent sign-in should set the marker again: " + marker);
        verify(identityClient, description("the cookie should be verified")).verifyRememberMeToken(series, token);
    }

    private HttpResponse<String> signIn() throws Exception {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        HttpResponse<String> form = browser.get("/sign-in");
        HttpResponse<String> signedIn = browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", "jo@example.com", "password", SECRET));
        assertEquals(303, signedIn.statusCode(), "the Visitor should be signed in");
        return signedIn;
    }

    private long lastAccessed() {
        String id = new String(Base64.getDecoder().decode(browser.cookie("SESSION")), StandardCharsets.UTF_8);
        try (RedisConnection connection = redis.getConnection()) {
            byte[] raw = connection.hashCommands().hGet(("sunrise:web:sessions:sessions:" + id).getBytes(StandardCharsets.UTF_8),
                    "lastAccessedTime".getBytes(StandardCharsets.UTF_8));
            return Long.parseLong(new String(raw, StandardCharsets.UTF_8));
        }
    }

    private void expireSession() {
        String id = new String(Base64.getDecoder().decode(browser.cookie("SESSION")), StandardCharsets.UTF_8);
        try (RedisConnection connection = redis.getConnection()) {
            connection.keyCommands().del(("sunrise:web:sessions:sessions:" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse(null);
    }
}
