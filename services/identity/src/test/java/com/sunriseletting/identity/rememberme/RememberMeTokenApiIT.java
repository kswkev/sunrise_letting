package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.sunriseletting.identity.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the whole service on a real port against PostgreSQL, with a clock the tests can move and Spring's web logging
 * turned up to debug, the level a developer would use to investigate a problem.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "logging.level.org.springframework.web=DEBUG")
@Import({TestcontainersConfiguration.class, RememberMeTokenApiIT.ClockForTests.class})
@ExtendWith(OutputCaptureExtension.class)
class RememberMeTokenApiIT {

    private static final String UNAUTHORIZED_BODY = "{\"error\":\"INVALID_REMEMBER_ME_TOKEN\"}";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MovableClock clock;

    @BeforeEach
    void startFromNoAccountsAndTheCurrentTime() {
        jdbc.sql("delete from accounts").update();
        clock.setTime(Instant.now());
    }

    private UUID createAccount(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO accounts (id, email, title, first_name, surname, phone, password_hash, role)
                        VALUES (:id, :email, 'Mr', 'Jo', 'Smith', '+447700900123', 'not-a-real-hash', :role)
                        """)
                .param("id", id)
                .param("email", email)
                .param("role", role)
                .update();
        return id;
    }

    private UUID createVisitor() {
        return createAccount("jo@example.com", "VISITOR");
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> issue(UUID accountId) throws IOException, InterruptedException {
        return post("/api/remember-me-tokens", "{\"accountId\":\"" + accountId + "\"}");
    }

    private HttpResponse<String> verifyToken(String series, String token) throws IOException, InterruptedException {
        Map<String, String> body = new HashMap<>();
        body.put("series", series);
        body.put("token", token);
        return post("/api/remember-me-tokens/verify", json.writeValueAsString(body));
    }

    private HttpResponse<String> delete(String series) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/remember-me-tokens/" + series))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Issued issueForVisitor(UUID accountId) throws IOException, InterruptedException {
        HttpResponse<String> response = issue(accountId);
        assertEquals(201, response.statusCode(), "the Visitor should get a token");
        JsonNode body = json.readTree(response.body());
        return new Issued(body.get("series").asString(), body.get("token").asString());
    }

    private Map<String, Object> storedRow(String series) {
        return jdbc.sql("select * from remember_me_tokens where series = :series").param("series", series).query().singleRow();
    }

    private int tokenCount() {
        return jdbc.sql("select count(*) from remember_me_tokens").query(Integer.class).single();
    }

    private static void assertUnauthorized(HttpResponse<String> response, String reason) {
        assertEquals(401, response.statusCode(), reason);
        assertEquals(UNAUTHORIZED_BODY, response.body(), "the response should not say why the token was rejected");
    }

    @Test
    void issueGivesASeriesAndTokenLasting30Days() throws Exception {
        UUID accountId = createVisitor();
        clock.setTime(Instant.parse("2026-11-03T10:00:00Z"));

        HttpResponse<String> response = issue(accountId);

        assertEquals(201, response.statusCode(), "a Visitor should get a token");
        JsonNode body = json.readTree(response.body());
        assertEquals(3, body.size(), "the response should only have the series, token and expiry");
        Map<String, Object> row = storedRow(body.get("series").asString());
        assertEquals(accountId, row.get("account_id"), "the series should belong to the account");
        assertEquals("2026-12-03T10:00:00Z", body.get("expiresAt").asString(), "the series should expire in 30 days");
        assertEquals(Timestamp.from(Instant.parse("2026-12-03T10:00:00Z")), row.get("expires_at"), "the response should give the stored expiry");
        assertNull(row.get("last_used_at"), "a new series has not been used");
    }

    @Test
    void storedValueIsTheHashOfTheTokenAndNotTheToken() throws Exception {
        Issued issued = issueForVisitor(createVisitor());

        Map<String, Object> row = storedRow(issued.series());

        assertEquals(RememberMeTokenValues.hash(issued.token()), row.get("token_hash"), "the hash of the token should be stored");
        assertThat(row.values().toString()).as("the raw token should not be stored").doesNotContain(issued.token());
    }

    @Test
    void verifyGivesTheAccountAndANewTokenAndTheOldTokenStopsWorking() throws Exception {
        UUID accountId = createVisitor();
        Issued issued = issueForVisitor(accountId);

        HttpResponse<String> response = verifyToken(issued.series(), issued.token());

        assertEquals(200, response.statusCode(), "a valid token should be accepted");
        JsonNode body = json.readTree(response.body());
        assertEquals(4, body.size(), "the response should only have the account, role, token and expiry");
        assertEquals(accountId.toString(), body.get("accountId").asString(), "the response should give the account");
        assertEquals("VISITOR", body.get("role").asString(), "the response should give the role");
        String newToken = body.get("token").asString();
        assertNotEquals(issued.token(), newToken, "the response should give a new token");
        Map<String, Object> row = storedRow(issued.series());
        assertEquals(RememberMeTokenValues.hash(newToken), row.get("token_hash"), "the hash of the new token should be stored");
        assertNotNull(row.get("last_used_at"), "the time of use should be stored");
        assertUnauthorized(verifyToken(issued.series(), issued.token()), "the old token should no longer work");
    }

    @Test
    void newTokenWorksTheNextTimeAndTheExpiryStaysTheSame() throws Exception {
        Issued issued = issueForVisitor(createVisitor());
        Object expiresAt = storedRow(issued.series()).get("expires_at");
        clock.setTime(clock.instant().plus(Duration.ofDays(1)));

        HttpResponse<String> first = verifyToken(issued.series(), issued.token());
        HttpResponse<String> second = verifyToken(issued.series(), json.readTree(first.body()).get("token").asString());

        assertEquals(200, second.statusCode(), "the swapped-in token should work");
        assertEquals(expiresAt, storedRow(issued.series()).get("expires_at"), "using a token should not extend the 30 days");
    }

    @Test
    void cameBackJustWithin30DaysIsAccepted() throws Exception {
        Issued issued = issueForVisitor(createVisitor());
        clock.setTime(clock.instant().plus(Duration.ofDays(30)).minusSeconds(1));

        assertEquals(200, verifyToken(issued.series(), issued.token()).statusCode(), "a token should work within its 30 days");
    }

    @Test
    void expiredSeriesIsRejectedAndRevokesNothing() throws Exception {
        Issued issued = issueForVisitor(createVisitor());
        clock.setTime(clock.instant().plus(Duration.ofDays(30)).plusSeconds(1));

        assertUnauthorized(verifyToken(issued.series(), issued.token()), "an expired series should be rejected");
        assertUnauthorized(verifyToken(issued.series(), "wrong-token"), "an expired series with a wrong token should be rejected");
        assertEquals(1, tokenCount(), "an expired series should not count as theft");
    }

    @Test
    void wrongTokenForAKnownSeriesRevokesEveryTokenOfThatAccountOnly() throws Exception {
        UUID accountId = createVisitor();
        Issued thisDevice = issueForVisitor(accountId);
        Issued otherDevice = issueForVisitor(accountId);
        Issued otherAccount = issueForVisitor(createAccount("other@example.com", "VISITOR"));

        assertUnauthorized(verifyToken(thisDevice.series(), "wrong-token"), "a wrong token should be rejected");

        assertUnauthorized(verifyToken(thisDevice.series(), thisDevice.token()), "the series should be revoked");
        assertUnauthorized(verifyToken(otherDevice.series(), otherDevice.token()), "the account's other series should be revoked");
        assertEquals(1, tokenCount(), "only the other account's token should be left");
        assertEquals(200, verifyToken(otherAccount.series(), otherAccount.token()).statusCode(),
                "another account's token should keep working");
    }

    @Test
    void replayingAReplacedTokenRevokesTheNewOneToo() throws Exception {
        Issued issued = issueForVisitor(createVisitor());
        HttpResponse<String> swapped = verifyToken(issued.series(), issued.token());
        String newToken = json.readTree(swapped.body()).get("token").asString();

        assertUnauthorized(verifyToken(issued.series(), issued.token()), "a replaced token should be rejected");

        assertUnauthorized(verifyToken(issued.series(), newToken), "someone may have stolen the token, so the new one is revoked too");
    }

    @Test
    void concurrentUsesOfTheSameTokenLetOnlyOneThrough() throws Exception {
        Issued issued = issueForVisitor(createVisitor());
        int requests = 4;
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            calls.add(() -> verifyToken(issued.series(), issued.token()).statusCode());
        }

        List<Integer> statuses = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            for (Future<Integer> status : executor.invokeAll(calls)) {
                statuses.add(status.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, statuses.stream().filter(status -> status == 200).count(), "a token should only be usable once");
    }

    @Test
    void unknownOrMalformedSeriesIsRejectedAndRevokesNothing() throws Exception {
        issueForVisitor(createVisitor());

        assertUnauthorized(verifyToken("aaaaaaaaaaaaaaaaaaaaaa", "any-token"), "an unknown series should be rejected");
        assertUnauthorized(verifyToken("not-a-series\u0000", "any-token"), "a malformed series should be rejected");
        assertUnauthorized(verifyToken(null, "any-token"), "a missing series should be rejected");
        assertEquals(1, tokenCount(), "nothing should be revoked");
    }

    @Test
    void missingTokenIsRejectedAndRevokesNothing() throws Exception {
        Issued issued = issueForVisitor(createVisitor());

        assertUnauthorized(verifyToken(issued.series(), null), "a missing token should be rejected");
        assertEquals(1, tokenCount(), "a malformed request should not count as theft");
    }

    @Test
    void propertyManagerGetsNoToken() throws Exception {
        HttpResponse<String> response = issue(createAccount("pm@example.com", "PROPERTY_MANAGER"));

        assertEquals(422, response.statusCode(), "a Property Manager should not get a token");
        assertEquals("{\"error\":\"REMEMBER_ME_NOT_ALLOWED\"}", response.body(), "the response should give the code");
        assertEquals(0, tokenCount(), "no token should be stored");
    }

    @Test
    void unknownOrMissingAccountGetsNoToken() throws Exception {
        assertEquals(422, issue(UUID.randomUUID()).statusCode(), "an unknown account should not get a token");
        assertEquals(422, post("/api/remember-me-tokens", "{}").statusCode(), "a request without an account should not get a token");
        assertEquals(0, tokenCount(), "no token should be stored");
    }

    @Test
    void deleteThenVerifyIsRejected() throws Exception {
        Issued issued = issueForVisitor(createVisitor());

        HttpResponse<String> deleted = delete(issued.series());

        assertEquals(204, deleted.statusCode(), "a revoke should give 204");
        assertEquals(0, tokenCount(), "the series should be gone");
        assertUnauthorized(verifyToken(issued.series(), issued.token()), "a revoked series should be rejected");
    }

    @Test
    void deleteOnlyRevokesThatSeries() throws Exception {
        UUID accountId = createVisitor();
        Issued signedOut = issueForVisitor(accountId);
        Issued otherDevice = issueForVisitor(accountId);

        delete(signedOut.series());

        assertEquals(200, verifyToken(otherDevice.series(), otherDevice.token()).statusCode(),
                "signing out on one device should not end the login on another");
    }

    @Test
    void deleteOfAnUnknownOrMalformedSeriesGivesNoContent() throws Exception {
        assertEquals(204, delete("aaaaaaaaaaaaaaaaaaaaaa").statusCode(), "an unknown series is already gone");
        assertEquals(204, delete("not-a-series").statusCode(), "a malformed series is already gone");
    }

    @Test
    void tokenValuesAreNeverLogged(CapturedOutput output) throws Exception {
        LoggerFactory.getLogger(getClass()).info("output capture is working");

        Issued issued = issueForVisitor(createVisitor());
        HttpResponse<String> swapped = verifyToken(issued.series(), issued.token());
        String newToken = json.readTree(swapped.body()).get("token").asString();
        verifyToken(issued.series(), "Wrong-token-value");

        assertEquals(200, swapped.statusCode(), "the token should be accepted");
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("Spring's debug logging should be on, or this test proves nothing")
                .contains("POST \"/api/remember-me-tokens/verify\"");
        assertThat(output.getAll()).as("the issued token should not be logged").doesNotContain(issued.token());
        assertThat(output.getAll()).as("the new token should not be logged").doesNotContain(newToken);
        assertThat(output.getAll()).as("a wrong token should not be logged").doesNotContain("Wrong-token-value");
    }

    private record Issued(String series, String token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockForTests {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    static class MovableClock extends Clock {

        private volatile Instant now = Instant.now();

        void setTime(Instant time) {
            now = time;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
