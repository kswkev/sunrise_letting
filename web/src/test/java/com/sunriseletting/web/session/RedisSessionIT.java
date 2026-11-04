package com.sunriseletting.web.session;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.WebApplication;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, RedisSessionIT.FixtureController.class })
@ExtendWith(OutputCaptureExtension.class)
class RedisSessionIT {

    private static final String KEY_PREFIX = "sunrise:web:sessions:sessions:";

    /** Test-only endpoints that put things in the session and read them back. */
    @RestController
    static class FixtureController {

        @GetMapping("/fixture/session/set")
        String set(@RequestParam String value, HttpSession session) {
            session.setAttribute("greeting", value);
            return "set";
        }

        @GetMapping("/fixture/session/get")
        String get(HttpSession session) {
            return String.valueOf(session.getAttribute("greeting"));
        }

        @GetMapping("/fixture/session/interval")
        String interval(@RequestParam(required = false) Integer seconds, HttpSession session) {
            if (seconds != null) {
                session.setMaxInactiveInterval(seconds);
            }
            return String.valueOf(session.getMaxInactiveInterval());
        }

        @GetMapping("/fixture/session/security/set")
        String setSecurity(HttpSession session) {
            Authentication token = UsernamePasswordAuthenticationToken.authenticated("someone", null,
                    List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(token));
            session.setAttribute("csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-value"));
            return "set";
        }

        @GetMapping("/fixture/session/security/get")
        String getSecurity(HttpSession session) {
            SecurityContext context = (SecurityContext) session
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            Authentication token = context.getAuthentication();
            CsrfToken csrf = (CsrfToken) session.getAttribute("csrf");
            return token.getPrincipal() + "|" + token.isAuthenticated() + "|" + token.getAuthorities() + "|" + csrf.getToken();
        }

        @GetMapping("/fixture/session/flash/set")
        org.springframework.web.servlet.view.RedirectView setFlash(RedirectAttributes attributes) {
            attributes.addFlashAttribute("flashed", true);
            return new org.springframework.web.servlet.view.RedirectView("/fixture/session/flash/get");
        }

        @GetMapping("/fixture/session/flash/get")
        String getFlash(org.springframework.ui.Model model) {
            return String.valueOf(model.getAttribute("flashed"));
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private RedisConnectionFactory redis;

    @Autowired
    private GenericContainer<?> redisContainer;

    @Test
    void sessionRepositoryIsTheRedisBackedOne() {
        assertInstanceOf(RedisSessionRepository.class, sessionRepository, "sessions should be stored in Redis");
    }

    @Test
    void sessionCookieHasTheAgreedAttributesAndTheSessionIsInRedis() throws Exception {
        HttpResponse<String> response = get(port, "/fixture/session/set?value=hello", null);

        String setCookie = response.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.startsWith("SESSION="), "the session cookie should be set: " + setCookie);
        assertTrue(setCookie.contains("HttpOnly"), "the session cookie should be HttpOnly");
        assertTrue(setCookie.contains("Secure"), "the session cookie should be Secure");
        assertTrue(setCookie.contains("SameSite=Lax"), "the session cookie should be SameSite=Lax");
        assertTrue(setCookie.contains("Path=/;") || setCookie.endsWith("Path=/"), "the session cookie should have path /");
        assertFalse(setCookie.contains("Max-Age"), "the session cookie should end with the browser");
        assertFalse(setCookie.contains("Expires"), "the session cookie should end with the browser");
        assertTrue(keyExists(sessionKey(cookie(response))), "the session should be found in Redis by its id, in web's namespace");
    }

    @Test
    void secondRequestWithTheCookieSeesTheAttribute() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=hello", null));

        HttpResponse<String> response = get(port, "/fixture/session/get", cookie);

        assertEquals("hello", response.body(), "the second request should see what the first stored");
    }

    @Test
    void attributesAreStoredAsJsonNotJavaSerialisation() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=hello", null));

        byte[] raw = rawAttribute(sessionKey(cookie), "greeting");

        assertNotNull(raw, "the attribute should be in Redis");
        assertFalse((raw[0] & 0xFF) == 0xAC && (raw[1] & 0xFF) == 0xED, "the attribute shouldn't be Java serialised");
        assertEquals("hello", JsonMapper.builder().build().readTree(raw).asString(), "the attribute should parse as JSON");
    }

    @Test
    void securityContextAndCsrfTokenRoundTripThroughRedis() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/security/set", null));

        HttpResponse<String> response = get(port, "/fixture/session/security/get", cookie);

        assertEquals(200, response.statusCode(), "the session should be readable from Redis");
        assertEquals("someone|true|[ROLE_VISITOR]|token-value", response.body(),
                "the principal, authorities and token value should survive");
    }

    @Test
    void flashAttributesRoundTripThroughRedisAndAreShownOnce() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/flash/set", null));

        assertEquals("true", get(port, "/fixture/session/flash/get", cookie).body(), "the flash attribute should reach the next request");
        assertEquals("null", get(port, "/fixture/session/flash/get", cookie).body(), "the flash attribute should be used only once");
    }

    @Test
    void defaultTimeoutIsThirtyMinutes() throws Exception {
        HttpResponse<String> response = get(port, "/fixture/session/interval", null);

        assertEquals("1800", response.body(), "a new session should time out after 30 minutes by default");
    }

    @Test
    void sessionKeepsItsOwnMaxInactiveIntervalInRedis() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/interval?seconds=7200", null));

        HttpResponse<String> response = get(port, "/fixture/session/interval", cookie);

        assertEquals("7200", response.body(), "the session's own time-out should be read back from Redis");
    }

    @Test
    void sessionRemovedFromRedisIsNotReturned() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=hello", null));
        try (RedisConnection connection = redis.getConnection()) {
            connection.keyCommands().del(sessionKey(cookie).getBytes(StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = get(port, "/fixture/session/get", cookie);

        assertEquals("null", response.body(), "the request should get a new, empty session");
        assertNotEquals(cookie, cookie(response), "the request should get a new session id");
    }

    @Test
    void sessionPastItsIntervalIsNotReturned() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=hello", null));
        get(port, "/fixture/session/interval?seconds=1", cookie);
        await().atMost(Duration.ofSeconds(10)).until(() -> !keyExists(sessionKey(cookie)));

        HttpResponse<String> response = get(port, "/fixture/session/get", cookie);

        assertEquals("null", response.body(), "an expired session should not be returned");
    }

    @Test
    void sessionCreatedThroughOneReplicaIsReadableThroughAnother() throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=shared", null));

        try (ConfigurableApplicationContext replica = new SpringApplicationBuilder(WebApplication.class, FixtureController.class)
                .properties(Map.of("server.port", "0",
                        "spring.data.redis.host", redisContainer.getHost(),
                        "spring.data.redis.port", String.valueOf(redisContainer.getMappedPort(6379))))
                .run()) {
            int replicaPort = ((ServletWebServerApplicationContext) replica).getWebServer().getPort();

            assertNotEquals(port, replicaPort, "the second context should be a separate server");
            assertEquals("shared", get(replicaPort, "/fixture/session/get", cookie).body(),
                    "a session created through one replica should be readable through the other");
        }
    }

    @Test
    void sessionIdsAreNotLogged(CapturedOutput output) throws Exception {
        String cookie = cookie(get(port, "/fixture/session/set?value=hello", null));
        get(port, "/fixture/session/get", cookie);

        assertFalse(output.getAll().contains(sessionId(cookie)), "the session id should never be logged");
        assertFalse(output.getAll().contains(cookie), "the cookie value should never be logged");
    }

    private HttpResponse<String> get(int onPort, String path, String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path)).GET();
        if (cookie != null) {
            request.header("Cookie", "SESSION=" + cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElse(null);
        assertNotNull(setCookie, "the response should set the session cookie");
        return setCookie.substring("SESSION=".length(), setCookie.indexOf(';'));
    }

    private String sessionId(String cookie) {
        return new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
    }

    private String sessionKey(String cookie) {
        return KEY_PREFIX + sessionId(cookie);
    }

    private boolean keyExists(String key) {
        try (RedisConnection connection = redis.getConnection()) {
            return Boolean.TRUE.equals(connection.keyCommands().exists(key.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private byte[] rawAttribute(String key, String attribute) {
        try (RedisConnection connection = redis.getConnection()) {
            return connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8),
                    ("sessionAttr:" + attribute).getBytes(StandardCharsets.UTF_8));
        }
    }
}
