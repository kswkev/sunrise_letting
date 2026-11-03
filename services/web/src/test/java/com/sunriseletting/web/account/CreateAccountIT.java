package com.sunriseletting.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.IdentityClient;
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

/** The whole sign-up journey over real HTTP, with sessions in real Redis and identity mocked. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreateAccountIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @MockitoBean
    private IdentityClient identityClient;

    @Autowired
    private RedisConnectionFactory redis;

    @BeforeEach
    void resetMock() {
        reset(identityClient);
    }

    @Test
    void signingUpFromTheHeaderEndsOnPropertiesWithTheBannerOnce() throws Exception {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        HttpResponse<String> form = get("/create-account", null);
        String anonymousCookie = cookie(form);
        assertNotNull(anonymousCookie, "opening the form should start a session for its CSRF token");

        HttpResponse<String> created = post("/create-account", anonymousCookie, formValues(token(form)));

        assertEquals(303, created.statusCode(), "success should answer See Other");
        assertEquals("/properties", path(created), "with no return address the Visitor goes to the search page");
        String signedInCookie = cookie(created);
        assertNotNull(signedInCookie, "the session id should change on sign-in");
        assertFalse(signedInCookie.equals(anonymousCookie), "the session id should change on sign-in");

        HttpResponse<String> properties = get("/properties", signedInCookie);
        assertEquals(1, Jsoup.parse(properties.body()).select(".notification-banner--success").size(), "the banner should show after sign-up");
        HttpResponse<String> reload = get("/properties", signedInCookie);
        assertEquals(0, Jsoup.parse(reload.body()).select(".notification-banner").size(), "the banner should not show on a reload");

        HttpResponse<String> againForm = get("/create-account", signedInCookie);
        assertEquals(302, againForm.statusCode(), "the session should be signed in on later requests");
        assertEquals("/properties", path(againForm), "a signed-in Visitor doesn't see the form");
        verify(identityClient, description("identity should be asked once")).createVisitor(any());
    }

    @Test
    void signingUpWithAReturnAddressGoesBackToIt() throws Exception {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        HttpResponse<String> form = get("/create-account?returnTo=%2Fproperties%2F123%2Fviewing", null);
        Map<String, String> values = formValues(token(form));
        values.put("returnTo", Jsoup.parse(form.body()).select("input[name=returnTo]").attr("value"));

        HttpResponse<String> created = post("/create-account", cookie(form), values);

        assertEquals(303, created.statusCode(), "success should answer See Other");
        assertEquals("/properties/123/viewing", path(created), "the Visitor should go back to the page they came from");
        verify(identityClient, description("identity should be asked once")).createVisitor(any());
    }

    @Test
    void formLeftOpenUntilItsSessionEndedCanBeSubmittedAgain() throws Exception {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        HttpResponse<String> form = get("/create-account", null);
        String cookie = cookie(form);
        String staleToken = token(form);
        deleteSession(cookie);

        HttpResponse<String> expired = post("/create-account", cookie, formValues(staleToken));

        assertEquals(200, expired.statusCode(), "an expired form should re-render, not answer 403");
        Document page = Jsoup.parse(expired.body());
        assertEquals("Your form has expired. Check your details and submit again.", page.select(".error-summary__body").text(),
                "the page should say the form expired");
        assertEquals("jo@example.com", page.selectFirst("#email").attr("value"), "values should be kept");
        assertFalse(expired.body().contains(SECRET), "the response shouldn't contain the password");
        verify(identityClient, never().description("identity shouldn't be called for an expired form")).createVisitor(any());

        String freshCookie = cookie(expired);
        assertNotNull(freshCookie, "the re-shown form should come with a new session");
        HttpResponse<String> created = post("/create-account", freshCookie, formValues(token(expired)));

        assertEquals(303, created.statusCode(), "submitting again should work as normal");
        verify(identityClient, times(1).description("identity should be asked once, on the second submit")).createVisitor(any());
    }

    @Test
    void wrongTokenOnALiveSessionAlsoReshowsTheFormAndTheFreshTokenWorks() throws Exception {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        HttpResponse<String> form = get("/create-account", null);
        String cookie = cookie(form);

        HttpResponse<String> expired = post("/create-account", cookie, formValues("not-the-token"));

        assertEquals(200, expired.statusCode(), "an invalid token should re-render, not answer 403");
        assertTrue(expired.body().contains("Your form has expired"), "the page should say the form expired");

        HttpResponse<String> created = post("/create-account", cookie, formValues(token(expired)));

        assertEquals(303, created.statusCode(), "the token on the re-shown form should work");
        verify(identityClient, description("identity should be asked once, on the second submit")).createVisitor(any());
    }

    private Map<String, String> formValues(String token) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("_csrf", token);
        values.put("title", "Mr");
        values.put("titleOther", "");
        values.put("firstName", "Jo");
        values.put("surname", "Bloggs");
        values.put("email", "jo@example.com");
        values.put("phone", "07700 900982");
        values.put("password", SECRET);
        values.put("confirmPassword", SECRET);
        return values;
    }

    private HttpResponse<String> get(String path, String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (cookie != null) {
            request.header("Cookie", "SESSION=" + cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String cookie, Map<String, String> values) throws IOException, InterruptedException {
        String body = values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", "SESSION=" + cookie)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().allValues("Set-Cookie").stream().filter(header -> header.startsWith("SESSION=")).findFirst().orElse(null);
        return setCookie == null ? null : setCookie.substring("SESSION=".length(), setCookie.indexOf(';'));
    }

    private String token(HttpResponse<String> response) {
        String token = Jsoup.parse(response.body()).select("input[name=_csrf]").attr("value");
        assertFalse(token.isBlank(), "the form should carry a CSRF token");
        return token;
    }

    private String path(HttpResponse<String> response) {
        return URI.create(response.headers().firstValue("Location").orElseThrow()).getPath();
    }

    private void deleteSession(String cookie) {
        String id = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
        try (RedisConnection connection = redis.getConnection()) {
            connection.keyCommands().del(("sunrise:web:sessions:sessions:" + id).getBytes(StandardCharsets.UTF_8));
        }
    }
}
