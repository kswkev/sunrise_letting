package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the whole service on a real port against PostgreSQL, with one Property Manager account seeded the way a
 * deployment would seed it, and with Spring's web and JDBC logging turned up, as a developer might to investigate a
 * problem. Each test starts from that one account with its temporary password.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "identity.property-managers[0].email=" + ChangePasswordApiIT.EMAIL,
        "identity.property-managers[0].temporary-password=" + ChangePasswordApiIT.TEMPORARY_PASSWORD,
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.jdbc=TRACE"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ChangePasswordApiIT {

    static final String EMAIL = "pat.manager@example.com";
    static final String TEMPORARY_PASSWORD = "Temporary-pw-1";

    private static final String NEW_PASSWORD = "Brand-new-pw-2";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private PropertyManagerSeeder seeder;

    @AfterEach
    void putTheSeededAccountBack() {
        jdbc.sql("delete from accounts").update();
        seeder.seed();
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authenticate(String email, String password) throws Exception {
        return post("/api/authentications", Map.of("email", email, "password", password));
    }

    private HttpResponse<String> change(UUID accountId, String newPassword, String confirmNewPassword) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("newPassword", newPassword);
        body.put("confirmNewPassword", confirmNewPassword);
        return post("/api/accounts/" + accountId + "/password", body);
    }

    private UUID seededAccountId() {
        return jdbc.sql("SELECT id FROM accounts WHERE email = :email").param("email", EMAIL).query(UUID.class).single();
    }

    private String storedHash() {
        return jdbc.sql("SELECT password_hash FROM accounts WHERE email = :email").param("email", EMAIL).query(String.class).single();
    }

    private boolean storedTemporaryFlag() {
        return jdbc.sql("SELECT password_temporary FROM accounts WHERE email = :email").param("email", EMAIL).query(Boolean.class).single();
    }

    private UUID createVisitor(String email, String password) throws Exception {
        HttpResponse<String> created = post("/api/accounts/visitors", Map.of(
                "title", "Mr",
                "firstName", "Jo",
                "surname", "Smith",
                "email", email,
                "phone", "07700 900123",
                "password", password,
                "confirmPassword", password));
        assertEquals(201, created.statusCode(), "the test account should be created");
        return UUID.fromString(json.readTree(created.body()).get("accountId").asString());
    }

    @Test
    void seededPropertyManagerAuthenticatesWithATemporaryPassword() throws Exception {
        HttpResponse<String> response = authenticate(EMAIL, TEMPORARY_PASSWORD);

        assertEquals(200, response.statusCode(), "the temporary password should sign in");
        JsonNode body = json.readTree(response.body());
        assertEquals("PROPERTY_MANAGER", body.get("role").asString(), "the response should give the Property Manager role");
        assertTrue(body.get("temporaryPassword").asBoolean(), "the response should say the password is temporary");
    }

    @Test
    void visitorAuthenticatesWithoutATemporaryPassword() throws Exception {
        createVisitor("jo@example.com", "Secret-pw-1");

        HttpResponse<String> response = authenticate("jo@example.com", "Secret-pw-1");

        assertEquals(200, response.statusCode(), "the Visitor should sign in");
        assertFalse(json.readTree(response.body()).get("temporaryPassword").asBoolean(), "a Visitor's password is not temporary");
    }

    @Test
    void wrongPasswordForAPropertyManagerGivesTheSameGenericErrorAsForAVisitor() throws Exception {
        createVisitor("jo@example.com", "Secret-pw-1");

        HttpResponse<String> propertyManager = authenticate(EMAIL, "Wrong-pw-1");
        HttpResponse<String> visitor = authenticate("jo@example.com", "Wrong-pw-1");

        assertEquals(401, propertyManager.statusCode(), "a wrong password should give 401");
        assertEquals("{\"error\":\"INVALID_CREDENTIALS\"}", propertyManager.body(), "the error should be the generic one");
        assertEquals(visitor.statusCode(), propertyManager.statusCode(), "both should have the same status");
        assertEquals(visitor.body(), propertyManager.body(), "both should have the same body");
    }

    @Test
    void validChangeReplacesThePasswordAndClearsTheFlag() throws Exception {
        UUID accountId = seededAccountId();

        HttpResponse<String> response = change(accountId, NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(204, response.statusCode(), "a valid change should give 204");
        assertEquals("", response.body(), "a 204 should have no body");
        assertFalse(storedTemporaryFlag(), "the password should no longer be temporary");
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, storedHash()), "the shared encoder should recognise the stored hash");
        assertEquals(401, authenticate(EMAIL, TEMPORARY_PASSWORD).statusCode(), "the old password should stop working");
        HttpResponse<String> signedIn = authenticate(EMAIL, NEW_PASSWORD);
        assertEquals(200, signedIn.statusCode(), "the new password should work");
        assertFalse(json.readTree(signedIn.body()).get("temporaryPassword").asBoolean(), "sign-in should report the flag as false");
    }

    @Test
    void secondChangeAfterSuccessIsRefusedAndChangesNothing() throws Exception {
        UUID accountId = seededAccountId();
        assertEquals(204, change(accountId, NEW_PASSWORD, NEW_PASSWORD).statusCode(), "the first change should succeed");
        String hashAfterFirstChange = storedHash();

        HttpResponse<String> second = change(accountId, "Another-pw-3", "Another-pw-3");

        assertEquals(409, second.statusCode(), "the second change should be refused");
        assertEquals("{\"error\":\"NO_TEMPORARY_PASSWORD\"}", second.body(), "the response should give the code");
        assertEquals(hashAfterFirstChange, storedHash(), "the password should stay as it was");
    }

    @Test
    void visitorCannotChangeTheirPasswordThisWay() throws Exception {
        UUID visitorId = createVisitor("jo@example.com", "Secret-pw-1");

        HttpResponse<String> response = change(visitorId, NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(409, response.statusCode(), "a Visitor has no temporary password to change");
        assertEquals(200, authenticate("jo@example.com", "Secret-pw-1").statusCode(), "the Visitor's password should still work");
    }

    @Test
    void unknownAccountGivesNotFound() throws Exception {
        HttpResponse<String> response = change(UUID.randomUUID(), NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(404, response.statusCode(), "an unknown account should give 404");
        assertEquals("{\"error\":\"ACCOUNT_NOT_FOUND\"}", response.body(), "the response should give the code");
    }

    static Stream<Arguments> invalidChanges() {
        return Stream.of(
                Arguments.of(null, null, "{\"errors\":{\"newPassword\":\"PASSWORD_REQUIRED\",\"confirmNewPassword\":\"CONFIRM_PASSWORD_REQUIRED\"}}"),
                Arguments.of("Aa1!", "Aa1!", "{\"errors\":{\"newPassword\":\"PASSWORD_TOO_SHORT\"}}"),
                Arguments.of("Aa1!" + "a".repeat(125), "Aa1!" + "a".repeat(125), "{\"errors\":{\"newPassword\":\"PASSWORD_TOO_LONG\"}}"),
                Arguments.of("alllowercase1!", "alllowercase1!", "{\"errors\":{\"newPassword\":\"PASSWORD_MISSING_CHARACTER_TYPES\"}}"),
                Arguments.of(TEMPORARY_PASSWORD, TEMPORARY_PASSWORD, "{\"errors\":{\"newPassword\":\"PASSWORD_SAME_AS_TEMPORARY\"}}"),
                Arguments.of(NEW_PASSWORD, "", "{\"errors\":{\"confirmNewPassword\":\"CONFIRM_PASSWORD_REQUIRED\"}}"),
                Arguments.of(NEW_PASSWORD, "Different-pw-1", "{\"errors\":{\"confirmNewPassword\":\"PASSWORDS_DO_NOT_MATCH\"}}"));
    }

    @ParameterizedTest
    @MethodSource("invalidChanges")
    void invalidChangeGivesItsCodeAndChangesNothing(String newPassword, String confirmNewPassword, String expectedBody) throws Exception {
        String hashBefore = storedHash();

        HttpResponse<String> response = change(seededAccountId(), newPassword, confirmNewPassword);

        assertEquals(400, response.statusCode(), "an invalid change should give 400");
        assertEquals(expectedBody, response.body(), "the response should give the failing field's code");
        assertEquals(hashBefore, storedHash(), "the password should stay as it was");
        assertTrue(storedTemporaryFlag(), "the password should still be temporary");
    }

    @Test
    void theStoreOnlyReplacesAPasswordThatIsStillTemporary() throws Exception {
        UUID accountId = seededAccountId();
        assertEquals(204, change(accountId, NEW_PASSWORD, NEW_PASSWORD).statusCode(), "the first change should succeed");
        String hashAfterChange = storedHash();

        boolean replaced = repository.replaceTemporaryPassword(accountId, "a-late-hash");

        assertFalse(replaced, "a change that loses a race should be told so");
        assertEquals(hashAfterChange, storedHash(), "a change that loses a race should not overwrite the password");
    }

    @Test
    void passwordsAreNeverLoggedOrReturned(CapturedOutput output) throws Exception {
        LoggerFactory.getLogger(getClass()).info("output capture is working");
        UUID accountId = seededAccountId();
        String mismatched = "Mismatched-pw-3";

        HttpResponse<String> sameAsTemporary = change(accountId, TEMPORARY_PASSWORD, TEMPORARY_PASSWORD);
        HttpResponse<String> mismatch = change(accountId, NEW_PASSWORD, mismatched);
        HttpResponse<String> accepted = change(accountId, NEW_PASSWORD, NEW_PASSWORD);
        HttpResponse<String> refused = change(accountId, NEW_PASSWORD, NEW_PASSWORD);

        assertEquals(204, accepted.statusCode(), "the valid change should be accepted");
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("Spring's debug logging should be on, or this test proves nothing")
                .contains("POST \"/api/accounts/" + accountId + "/password\"");
        assertThat(output.getOut()).as("JDBC trace logging should be on, or this test proves nothing")
                .contains("Executing prepared SQL");
        assertThat(output.getAll()).as("the new password should not be logged").doesNotContain(NEW_PASSWORD);
        assertThat(output.getAll()).as("the temporary password should not be logged").doesNotContain(TEMPORARY_PASSWORD);
        assertThat(output.getAll()).as("the mismatched confirmation should not be logged").doesNotContain(mismatched);
        for (HttpResponse<String> response : List.of(sameAsTemporary, mismatch, accepted, refused)) {
            assertThat(response.body()).as("the response should not contain a password")
                    .doesNotContain(NEW_PASSWORD, TEMPORARY_PASSWORD, mismatched);
        }
    }
}
