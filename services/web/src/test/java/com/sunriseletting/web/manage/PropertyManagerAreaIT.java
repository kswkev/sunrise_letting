package com.sunriseletting.web.manage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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

/** A real Property Manager sign-in through to the area, over real HTTP, with sessions in real Redis and identity mocked. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PropertyManagerAreaIT {

    private static final String EMAIL = "pm@example.com";

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
        browser = new CookieJarClient(port);
    }

    @Test
    void aRealSignInRendersTheTwoHourDialogOnManagerAndPublicPages() throws Exception {
        signIn(false, "/manage");

        for (String path : List.of("/manage", "/properties")) {
            HttpResponse<String> response = browser.get(path);

            assertEquals(200, response.statusCode(), path + " should load");
            Document page = Jsoup.parse(response.body());
            assertEquals("7200", page.select("#timeout-dialog").attr("data-timeout-seconds"), "the session's own two hours should reach " + path);
            assertEquals("120", page.select("#timeout-dialog").attr("data-warning-seconds"), "the warning is 2 minutes before the end, so at 1 hour 58 minutes, on " + path);
            assertEquals(1, page.select("head script[src=/js/session-timeout.js]").size(), "the script that opens the dialog should be on " + path);
            assertEquals(List.of("Properties to let", "Property Manager area", "Sign out"), page.select("header nav li").eachText(), "the header on " + path);
        }
        verifySignedIn();
    }

    @Test
    void aTemporaryPasswordIsRedirectedFromTheAreaButNotFromChangePasswordOrPublicPages() throws Exception {
        signIn(true, "/manage/change-password");

        HttpResponse<String> home = browser.get("/manage");
        assertEquals(302, home.statusCode(), "the home page should redirect");
        assertEquals("/manage/change-password", location(home), "to the change-password page");
        assertEquals(302, browser.get("/manage/other").statusCode(), "so should any other page");
        assertEquals(404, browser.get("/manage/change-password").statusCode(), "no redirect loop: the page comes with #34, so it is a 404 until then");
        assertEquals(200, browser.get("/properties").statusCode(), "public pages open as normal");
        verifySignedIn();
    }

    @Test
    void signingOutEndsTheSessionSoTheAreaGoesToSignIn() throws Exception {
        signIn(false, "/manage");
        HttpResponse<String> home = browser.get("/manage");
        assertEquals(200, home.statusCode(), "the area should load while signed in");

        HttpResponse<String> signedOut = browser.post("/sign-out", Map.of("_csrf", CookieJarClient.csrfToken(home)));

        assertEquals(303, signedOut.statusCode(), "sign out should work from the area");
        assertEquals("/properties", location(signedOut), "sign out lands on the search page");
        HttpResponse<String> after = browser.get("/manage");
        assertEquals(302, after.statusCode(), "the area should no longer open");
        assertEquals("/sign-in?returnTo=%2Fmanage", location(after), "signing out isn't a time-out, so no message");
        verifySignedIn();
    }

    @Test
    void anExpiredSessionGoesToTheTimedOutAddress() throws Exception {
        signIn(false, "/manage");
        expireSession();

        HttpResponse<String> after = browser.get("/manage");

        assertEquals(302, after.statusCode(), "the area should no longer open");
        assertEquals("/sign-in?timedOut=true&returnTo=%2Fmanage", location(after), "with the message and the return address");
        verifySignedIn();
    }

    private void signIn(boolean temporaryPassword, String expectedDestination) throws Exception {
        when(identityClient.authenticate(EMAIL, SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-2", AccountRole.PROPERTY_MANAGER, temporaryPassword));
        HttpResponse<String> form = browser.get("/sign-in");
        HttpResponse<String> signedIn = browser.post("/sign-in", Map.of("_csrf", CookieJarClient.csrfToken(form), "email", EMAIL, "password", SECRET));
        assertEquals(303, signedIn.statusCode(), "the Property Manager should be signed in");
        assertEquals(expectedDestination, location(signedIn), "where sign-in sends them");
    }

    private void verifySignedIn() {
        verify(identityClient, description("identity should check the details")).authenticate(EMAIL, SECRET);
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
