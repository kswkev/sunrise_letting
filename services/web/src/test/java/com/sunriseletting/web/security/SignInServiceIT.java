package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import com.sunriseletting.web.TestcontainersConfiguration;
import com.sunriseletting.web.WebApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sign-in against real sessions in Redis. Test-only endpoints stand in for the pages that will sign people in. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, SignInServiceIT.FixtureController.class })
class SignInServiceIT {

    private static final String KEY_PREFIX = "sunrise:web:sessions:sessions:";

    @RestController
    static class FixtureController {

        private final SignInService signInService;

        FixtureController(SignInService signInService) {
            this.signInService = signInService;
        }

        @GetMapping("/fixture/anonymous-session")
        String anonymousSession(HttpSession session) {
            session.setAttribute("kept", "yes");
            return "ok";
        }

        @GetMapping("/fixture/sign-in")
        String signIn(HttpServletRequest request, HttpServletResponse response) {
            signInService.signIn("account-1", AccountRole.VISITOR, request, response);
            return "ok";
        }

        @GetMapping("/fixture/who-am-i")
        String whoAmI(@AuthenticationPrincipal SessionPrincipal principal, HttpSession session) {
            return principal == null ? "nobody" : principal.accountId() + "|" + principal.role() + "|" + session.getAttribute("kept");
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private RedisConnectionFactory redis;

    @Autowired
    private GenericContainer<?> redisContainer;

    @Test
    void signingInChangesTheSessionIdAndTheOldIdIsNoLongerInRedis() throws Exception {
        String oldCookie = cookie(get(port, "/fixture/anonymous-session", null));
        assertTrue(keyExists(sessionKey(oldCookie)), "the anonymous session should be in Redis");

        String newCookie = cookie(get(port, "/fixture/sign-in", oldCookie));

        assertNotEquals(oldCookie, newCookie, "the session id should change on sign-in");
        assertFalse(keyExists(sessionKey(oldCookie)), "the old id should no longer be in Redis");
        assertTrue(keyExists(sessionKey(newCookie)), "the new id should be in Redis");
    }

    @Test
    void signedInSessionHasTheVisitorTimeoutInRedis() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in", null));

        byte[] raw = rawField(sessionKey(cookie), "maxInactiveInterval");

        assertEquals(1800, JsonMapper.builder().build().readTree(raw).asInt(), "the stored session should idle out after 30 minutes");
    }

    @Test
    void storedPrincipalHoldsOnlyTheAccountIdAndRole() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in", null));

        byte[] raw = rawField(sessionKey(cookie), "sessionAttr:SPRING_SECURITY_CONTEXT");

        JsonNode principal = JsonMapper.builder().build().readTree(raw).get("authentication").get("principal");
        assertEquals("account-1", principal.get("accountId").asString(), "the principal should hold the account id");
        Set<String> fields = new java.util.HashSet<>(principal.propertyNames());
        fields.remove("@class");
        assertEquals(Set.of("accountId", "role"), fields, "the principal should hold nothing but the account id and role");
        assertTrue(principal.get("role").toString().contains("VISITOR"), "the principal should hold the role");
    }

    @Test
    void laterRequestsSeeThePrincipalAndWhatTheSessionAlreadyHeld() throws Exception {
        String oldCookie = cookie(get(port, "/fixture/anonymous-session", null));
        String cookie = cookie(get(port, "/fixture/sign-in", oldCookie));

        HttpResponse<String> response = get(port, "/fixture/who-am-i", cookie);

        assertEquals(200, response.statusCode(), "the session should be readable from Redis");
        assertEquals("account-1|VISITOR|yes", response.body(), "the next request should see the same principal");
    }

    @Test
    void anotherReplicaSeesTheSamePrincipal() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in", null));

        try (ConfigurableApplicationContext replica = new SpringApplicationBuilder(WebApplication.class, FixtureController.class)
                .properties(Map.of("server.port", "0",
                        "spring.data.redis.host", redisContainer.getHost(),
                        "spring.data.redis.port", String.valueOf(redisContainer.getMappedPort(6379))))
                .run()) {
            int replicaPort = ((ServletWebServerApplicationContext) replica).getWebServer().getPort();

            assertEquals("account-1|VISITOR|null", get(replicaPort, "/fixture/who-am-i", cookie).body(),
                    "a request handled by another replica should see the same principal");
        }
    }

    @Test
    void noOneIsSignedInWithoutSigningIn() throws Exception {
        String cookie = cookie(get(port, "/fixture/anonymous-session", null));

        assertEquals("nobody", get(port, "/fixture/who-am-i", cookie).body(), "an anonymous session has no principal");
    }

    private HttpResponse<String> get(int onPort, String path, String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path)).GET();
        if (cookie != null) {
            request.header("Cookie", "SESSION=" + cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().allValues("Set-Cookie").stream().filter(header -> header.startsWith("SESSION=")).findFirst().orElse(null);
        assertNotNull(setCookie, "the response should set the session cookie");
        return setCookie.substring("SESSION=".length(), setCookie.indexOf(';'));
    }

    private String sessionKey(String cookie) {
        return KEY_PREFIX + new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
    }

    private boolean keyExists(String key) {
        try (RedisConnection connection = redis.getConnection()) {
            return Boolean.TRUE.equals(connection.keyCommands().exists(key.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private byte[] rawField(String key, String field) {
        try (RedisConnection connection = redis.getConnection()) {
            return connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8));
        }
    }
}
