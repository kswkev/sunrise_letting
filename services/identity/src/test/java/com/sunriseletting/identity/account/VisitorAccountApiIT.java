package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
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
 * Runs the whole service on a real port against PostgreSQL, with Spring's web logging turned up to debug,
 * the level a developer would use to investigate a problem.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "logging.level.org.springframework.web=DEBUG")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class VisitorAccountApiIT {

    private static final String PASSWORD = "Secret-pw-1";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void emptyTheAccountsTable() {
        jdbc.sql("delete from accounts").update();
    }

    private static Map<String, String> validRequest() {
        return new HashMap<>(Map.of(
                "title", "Mr",
                "firstName", "Jo",
                "surname", "Smith",
                "email", "jo@example.com",
                "phone", "07700 900123",
                "password", PASSWORD,
                "confirmPassword", PASSWORD));
    }

    private static Map<String, String> validRequestWith(String field, String value) {
        Map<String, String> request = validRequest();
        request.put(field, value);
        return request;
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> createAccount(Map<String, String> body) throws IOException, InterruptedException {
        return post("/api/accounts/visitors", body);
    }

    private int accountCount() {
        return jdbc.sql("select count(*) from accounts").query(Integer.class).single();
    }

    private Map<String, Object> storedAccount() {
        return jdbc.sql("select * from accounts").query().singleRow();
    }

    @Test
    void validRequestStoresTheAccountAndGivesItsIdAndRole() throws Exception {
        HttpResponse<String> response = createAccount(validRequestWith("email", " Jo@Example.com "));

        assertEquals(201, response.statusCode(), "a valid request should create an account");
        JsonNode body = json.readTree(response.body());
        Map<String, Object> row = storedAccount();
        assertEquals(row.get("id").toString(), body.get("accountId").asString(), "the response should give the stored id");
        assertEquals("VISITOR", body.get("role").asString(), "the response should give the Visitor role");
        assertEquals(2, body.size(), "the response should only have the id and the role");
        assertEquals("jo@example.com", row.get("email"), "the email should be stored trimmed and lower-cased");
        assertEquals("+447700900123", row.get("phone"), "the phone should be stored in E.164");
        assertEquals("VISITOR", row.get("role"), "the stored role should be Visitor");
        String hash = (String) row.get("password_hash");
        assertTrue(hash.startsWith("$2"), "the password should be stored as a BCrypt hash");
        assertTrue(passwordEncoder.matches(PASSWORD, hash), "the hash should match the submitted password");
        assertNotNull(row.get("created_at"), "the time of creation should be stored");
    }

    @Test
    void roleInTheRequestIsIgnored() throws Exception {
        HttpResponse<String> response = createAccount(validRequestWith("role", "PROPERTY_MANAGER"));

        assertEquals(201, response.statusCode(), "an extra field should not stop the account being created");
        assertEquals("VISITOR", storedAccount().get("role"), "a request should not be able to choose its role");
    }

    @Test
    void emailAlreadyRegisteredIsRejectedIgnoringCaseAndSpaces() throws Exception {
        createAccount(validRequest());

        HttpResponse<String> response = createAccount(validRequestWith("email", " Jo@Example.com "));

        assertEquals(409, response.statusCode(), "the same email in a different form should be a duplicate");
        assertEquals("ACCOUNT_NOT_CREATED", json.readTree(response.body()).get("error").asString(),
                "the response should only say the account was not created");
        assertEquals(1, accountCount(), "no second account should be created");
    }

    @Test
    void concurrentRequestsForTheSameEmailCreateOneAccount() throws Exception {
        int requests = 4;
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            calls.add(() -> createAccount(validRequest()).statusCode());
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

        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), "exactly one request should succeed");
        assertEquals(requests - 1, statuses.stream().filter(status -> status == 409).count(),
                "the other requests should be duplicates");
        assertEquals(1, accountCount(), "only one account should be created");
    }

    @Test
    void invalidFieldsGiveTheirCodesAndCreateNoAccount() throws Exception {
        Map<String, String> request = validRequestWith("email", "not-an-email");
        request.put("phone", "+1 202 555 0100");
        request.put("confirmPassword", "different");

        HttpResponse<String> response = createAccount(request);

        assertEquals(400, response.statusCode(), "invalid fields should be rejected");
        JsonNode errors = json.readTree(response.body()).get("errors");
        assertEquals("EMAIL_INVALID", errors.get("email").asString(), "the email should have its code");
        assertEquals("PHONE_INVALID", errors.get("phone").asString(), "the phone should have its code");
        assertEquals("PASSWORDS_DO_NOT_MATCH", errors.get("confirmPassword").asString(), "the confirm password should have its code");
        assertEquals(3, errors.size(), "only the failing fields should have codes");
        assertEquals(0, accountCount(), "no account should be created");
    }

    @Test
    void phoneWithCharactersThatAreNotAllowedHasItsOwnCode() throws Exception {
        HttpResponse<String> response = createAccount(validRequestWith("phone", "0113-496-0000"));

        assertEquals(400, response.statusCode(), "a phone with hyphens should be rejected");
        assertEquals("PHONE_INVALID_CHARACTERS", json.readTree(response.body()).get("errors").get("phone").asString(),
                "the phone should have the code for characters that are not allowed");
        assertEquals(0, accountCount(), "no account should be created");
    }

    @Test
    void valueOverItsMaximumLengthIsRejected() throws Exception {
        HttpResponse<String> response = createAccount(validRequestWith("firstName", "x".repeat(101)));

        assertEquals(400, response.statusCode(), "an over-long value should be rejected");
        assertEquals("FIRST_NAME_TOO_LONG", json.readTree(response.body()).get("errors").get("firstName").asString(),
                "the field should have its code");
        assertEquals(0, accountCount(), "no account should be created");
    }

    @Test
    void duplicateEmailAlongsideAnInvalidFieldGivesValidationErrorsOnly() throws Exception {
        createAccount(validRequest());

        HttpResponse<String> response = createAccount(validRequestWith("firstName", ""));

        assertEquals(400, response.statusCode(), "the duplicate check should only run when every field is valid");
        assertEquals("{\"errors\":{\"firstName\":\"FIRST_NAME_REQUIRED\"}}", response.body(),
                "the response should not hint that the email is registered");
        assertEquals(1, accountCount(), "no second account should be created");
    }

    @Test
    void otherTitleTextIsStoredOnlyWhenTitleIsOther() throws Exception {
        createAccount(validRequestWith("titleOther", "xyz"));
        assertNull(storedAccount().get("title_other"), "the free-text title should not be stored when Title is Mr");

        jdbc.sql("delete from accounts").update();
        Map<String, String> request = validRequestWith("title", "Other");
        request.put("titleOther", "Rev");
        createAccount(request);
        assertEquals("Other", storedAccount().get("title"), "the title should be stored");
        assertEquals("Rev", storedAccount().get("title_other"), "the free-text title should be stored when Title is Other");
    }

    @Test
    void submittedPasswordIsNeverLoggedStoredOrReturned(CapturedOutput output) throws Exception {
        LoggerFactory.getLogger(getClass()).info("output capture is working");

        HttpResponse<String> created = createAccount(validRequest());
        HttpResponse<String> duplicate = createAccount(validRequest());
        HttpResponse<String> invalid = createAccount(validRequestWith("firstName", ""));
        HttpResponse<String> validated = post("/api/account-validation/password", Map.of("value", PASSWORD));

        assertEquals(201, created.statusCode(), "the first request should create the account");
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("Spring's debug logging should be on, or this test proves nothing")
                .contains("POST \"/api/accounts/visitors\"");
        assertThat(output.getAll()).as("the password should not be logged").doesNotContain(PASSWORD);
        assertThat(created.body()).as("the created response should not contain the password").doesNotContain(PASSWORD);
        assertThat(duplicate.body()).as("the duplicate response should not contain the password").doesNotContain(PASSWORD);
        assertThat(invalid.body()).as("the invalid response should not contain the password").doesNotContain(PASSWORD);
        assertThat(validated.body()).as("the inline check should not contain the password").doesNotContain(PASSWORD);
        assertThat(storedAccount().values().toString()).as("the password should not be stored in plain text")
                .doesNotContain(PASSWORD);
    }

    @Test
    void inlineEmailCheckNeverRevealsAnExistingAccount() throws Exception {
        createAccount(validRequest());

        HttpResponse<String> response = post("/api/account-validation/email", Map.of("value", "jo@example.com"));

        assertEquals(200, response.statusCode(), "the inline check should succeed");
        assertEquals("{\"error\":null}", response.body(), "a registered email in a valid format should give no error");
    }

    static Stream<Arguments> passwordsOverSeventyTwoBytes() {
        return Stream.of(
                Arguments.of("Aa1!" + "a".repeat(124), "Aa1!" + "a".repeat(123) + "x"),
                Arguments.of("Aa1!" + "é".repeat(40), "Aa1!" + "é".repeat(39) + "x"));
    }

    @ParameterizedTest
    @MethodSource("passwordsOverSeventyTwoBytes")
    void passwordOverSeventyTwoBytesCreatesAnAccount(String password, String differsAfterByte72, CapturedOutput output)
            throws Exception {
        assertTrue(password.getBytes(StandardCharsets.UTF_8).length > 72, "the test password should be over 72 bytes");

        HttpResponse<String> response = createAccount(passwordRequest(password));

        assertEquals(201, response.statusCode(), "a valid long password should create an account");
        String hash = (String) storedAccount().get("password_hash");
        assertTrue(hash.startsWith("$2"), "the password should be stored as a BCrypt hash");
        assertTrue(passwordEncoder.matches(password, hash), "the encoder should accept the original password");
        assertFalse(passwordEncoder.matches(differsAfterByte72, hash),
                "the encoder should reject a password differing only after byte 72");
        assertThat(response.body()).as("the response should not contain the password").doesNotContain(password);
        assertThat(output.getOut()).as("Spring's debug logging should be on, or this test proves nothing")
                .contains("POST \"/api/accounts/visitors\"");
        assertThat(output.getAll()).as("the password should not be logged").doesNotContain(password);
    }

    private static Map<String, String> passwordRequest(String password) {
        Map<String, String> request = validRequestWith("password", password);
        request.put("confirmPassword", password);
        return request;
    }
}
