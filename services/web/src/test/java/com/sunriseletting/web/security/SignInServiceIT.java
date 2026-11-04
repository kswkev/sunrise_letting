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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

        @GetMapping("/fixture/remembered-session")
        String rememberedSession(HttpSession session) {
            session.setAttribute(RememberMeLogin.SESSION_ATTRIBUTE, Boolean.TRUE);
            session.setAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE, "2099-01-01T00:00:00Z");
            return "ok";
        }

        @GetMapping("/fixture/remembered-attributes")
        String rememberedAttributes(HttpSession session) {
            return session.getAttribute(RememberMeLogin.SESSION_ATTRIBUTE) + "|" + session.getAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE)
                    + "|" + session.getMaxInactiveInterval();
        }

        @GetMapping("/fixture/sign-in")
        String signIn(HttpServletRequest request, HttpServletResponse response) {
            signInService.signIn(new SessionPrincipal("account-1", AccountRole.VISITOR, false), request, response);
            return "ok";
        }

        @GetMapping("/fixture/sign-in-manager")
        String signInManager(@RequestParam boolean temporary, HttpServletRequest request, HttpServletResponse response) {
            signInService.signIn(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, temporary), request, response);
            return "ok";
        }

        @GetMapping("/fixture/who-am-i")
        String whoAmI(@AuthenticationPrincipal SessionPrincipal principal, HttpSession session) {
            return principal == null ? "nobody" : principal.accountId() + "|" + principal.role() + "|" + session.getAttribute("kept");
        }

        @GetMapping("/fixture/temporary-password")
        String temporaryPassword(@AuthenticationPrincipal SessionPrincipal principal) {
            return String.valueOf(principal.temporaryPassword());
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
    void signedInSessionHasThePropertyManagerTimeoutInRedis() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in-manager?temporary=false", null));

        byte[] raw = rawField(sessionKey(cookie), "maxInactiveInterval");

        assertEquals(7200, JsonMapper.builder().build().readTree(raw).asInt(), "the stored session should idle out after 2 hours");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void theTemporaryPasswordFlagSurvivesRedis(boolean temporary) throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in-manager?temporary=" + temporary, null));

        HttpResponse<String> response = get(port, "/fixture/temporary-password", cookie);

        assertEquals(200, response.statusCode(), "the session should be readable from Redis");
        assertEquals(String.valueOf(temporary), response.body(), "the next request should see the flag");
    }

    @Test
    void aSessionStoredBeforeTheFlagExistedStaysSignedInAndCountsAsNotTemporary() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in-manager?temporary=true", null));
        removeTheFlagFromTheStoredPrincipal(sessionKey(cookie));

        HttpResponse<String> whoAmI = get(port, "/fixture/who-am-i", cookie);
        HttpResponse<String> flag = get(port, "/fixture/temporary-password", cookie);
        HttpResponse<String> signInPage = get(port, "/sign-in", cookie);

        assertEquals(200, whoAmI.statusCode(), "the old session should still be readable from Redis");
        assertEquals("account-2|PROPERTY_MANAGER|null", whoAmI.body(), "the old session should still be signed in as the same account");
        assertEquals("false", flag.body(), "with no flag stored, the password counts as not temporary");
        assertEquals(302, signInPage.statusCode(), "the old session should still count as signed in on the sign-in page");
        assertEquals("/manage", signInPage.headers().firstValue("Location").orElse(null), "the old session should be routed as a Property Manager");
    }

    @Test
    void storedPrincipalHoldsOnlyTheAccountIdRoleAndFlag() throws Exception {
        String cookie = cookie(get(port, "/fixture/sign-in", null));

        byte[] raw = rawField(sessionKey(cookie), "sessionAttr:SPRING_SECURITY_CONTEXT");

        JsonNode principal = JsonMapper.builder().build().readTree(raw).get("authentication").get("principal");
        assertEquals("account-1", principal.get("accountId").asString(), "the principal should hold the account id");
        Set<String> fields = new java.util.HashSet<>(principal.propertyNames());
        fields.remove("@class");
        assertEquals(Set.of("accountId", "role", "temporaryPassword"), fields, "the principal should hold nothing but the account id, role and flag");
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
    void whatAnEarlierRememberedLoginLeftIsGoneWhenTheSessionIsReadBackFromRedis() throws Exception {
        String oldCookie = cookie(get(port, "/fixture/remembered-session", null));
        String cookie = cookie(get(port, "/fixture/sign-in", oldCookie));

        assertEquals("null|null|1800", get(port, "/fixture/remembered-attributes", cookie).body(),
                "the next request should see an ordinary session, not the earlier login's flag, expiry or long life");
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

    private void removeTheFlagFromTheStoredPrincipal(String key) {
        JsonMapper mapper = JsonMapper.builder().build();
        byte[] field = "sessionAttr:SPRING_SECURITY_CONTEXT".getBytes(StandardCharsets.UTF_8);
        JsonNode context = mapper.readTree(rawField(key, "sessionAttr:SPRING_SECURITY_CONTEXT"));
        ObjectNode principal = (ObjectNode) context.get("authentication").get("principal");
        assertNotNull(principal.remove("temporaryPassword"), "the stored principal should have held the flag");
        try (RedisConnection connection = redis.getConnection()) {
            connection.hashCommands().hSet(key.getBytes(StandardCharsets.UTF_8), field, mapper.writeValueAsBytes(context));
        }
    }

    private byte[] rawField(String key, String field) {
        try (RedisConnection connection = redis.getConnection()) {
            return connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8));
        }
    }
}
