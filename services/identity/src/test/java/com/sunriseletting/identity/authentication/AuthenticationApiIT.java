package com.sunriseletting.identity.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class AuthenticationApiIT {

    private static final String EMAIL = "jo@example.com";
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

    private HttpResponse<String> post(String path, Map<String, String> body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authenticate(String email, String password) throws IOException, InterruptedException {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        return post("/api/authentications", body);
    }

    /** Signs up through the real API, so these tests prove sign-up and sign-in agree on how passwords are stored. */
    private UUID createVisitor(String password) throws IOException, InterruptedException {
        HttpResponse<String> created = post("/api/accounts/visitors", Map.of(
                "title", "Mr",
                "firstName", "Jo",
                "surname", "Smith",
                "email", EMAIL,
                "phone", "07700 900123",
                "password", password,
                "confirmPassword", password));
        assertEquals(201, created.statusCode(), "the test account should be created");
        return UUID.fromString(json.readTree(created.body()).get("accountId").asString());
    }

    private static Map<String, List<String>> headersThatShouldNotVary(HttpResponse<String> response) {
        Map<String, List<String>> headers = new TreeMap<>(response.headers().map());
        headers.remove("date");
        return headers;
    }

    @Test
    void correctDetailsGiveTheAccountIdAndVisitorRole() throws Exception {
        UUID accountId = createVisitor(PASSWORD);

        HttpResponse<String> response = authenticate(EMAIL, PASSWORD);

        assertEquals(200, response.statusCode(), "correct details should be accepted");
        JsonNode body = json.readTree(response.body());
        assertEquals(accountId.toString(), body.get("accountId").asString(), "the response should give the account's id");
        assertEquals("VISITOR", body.get("role").asString(), "the response should give the Visitor role");
        assertEquals(2, body.size(), "the response should only have the id and the role");
    }

    @Test
    void emailWithDifferentCapitalsAndSpacesIsAccepted() throws Exception {
        createVisitor(PASSWORD);

        HttpResponse<String> response = authenticate("  JO@example.COM ", PASSWORD);

        assertEquals(200, response.statusCode(), "capitals and surrounding spaces should not stop the sign-in");
    }

    @Test
    void passwordOverSeventyTwoBytesIsCheckedInFull() throws Exception {
        String password = "Aa1!" + "a".repeat(124);
        String differsAfterByte72 = "Aa1!" + "a".repeat(123) + "x";
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).as("the test password should be over 72 bytes")
                .isGreaterThan(72);
        createVisitor(password);

        assertEquals(200, authenticate(EMAIL, password).statusCode(), "the full password should be accepted");
        assertEquals(401, authenticate(EMAIL, differsAfterByte72).statusCode(),
                "a password differing only after byte 72 should be rejected");
    }

    @Test
    void propertyManagerAuthenticatesWithTheirRole() throws Exception {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO accounts (id, email, title, first_name, surname, phone, password_hash, role)
                        VALUES (:id, 'pm@example.com', 'Mr', 'Pat', 'Manager', '+447700900123', :hash, 'PROPERTY_MANAGER')
                        """)
                .param("id", accountId)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .update();

        HttpResponse<String> response = authenticate("pm@example.com", PASSWORD);

        assertEquals(200, response.statusCode(), "the check should work for any role");
        assertEquals("PROPERTY_MANAGER", json.readTree(response.body()).get("role").asString(),
                "the response should give the Property Manager role");
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        createVisitor(PASSWORD);

        HttpResponse<String> wrongPassword = authenticate(EMAIL, "Wrong-pw-1");
        HttpResponse<String> unknownEmail = authenticate("nobody@example.com", PASSWORD);

        assertEquals(401, wrongPassword.statusCode(), "a wrong password should give 401");
        assertEquals("{\"error\":\"INVALID_CREDENTIALS\"}", wrongPassword.body(), "a wrong password should give the generic error");
        assertEquals(wrongPassword.statusCode(), unknownEmail.statusCode(), "both should have the same status");
        assertEquals(wrongPassword.body(), unknownEmail.body(), "both should have the same body");
        assertEquals(headersThatShouldNotVary(wrongPassword), headersThatShouldNotVary(unknownEmail),
                "both should have the same headers");
    }

    @Test
    void emailThatTheDataStoreCannotHoldIsAnUnknownEmail() throws Exception {
        HttpResponse<String> response = authenticate("jo\u0000@example.com", PASSWORD);

        assertEquals(401, response.statusCode(), "PostgreSQL text can't hold NUL, so no account can have this email");
        assertEquals("{\"error\":\"INVALID_CREDENTIALS\"}", response.body(), "the response should be the generic error");
    }

    @Test
    void emptyFieldsGiveTheirCodes() throws Exception {
        HttpResponse<String> both = authenticate("", "");
        HttpResponse<String> emailOnly = authenticate("   ", PASSWORD);
        HttpResponse<String> passwordOnly = authenticate(EMAIL, null);

        assertEquals(400, both.statusCode(), "empty fields should be rejected");
        JsonNode errors = json.readTree(both.body()).get("errors");
        assertEquals("EMAIL_REQUIRED", errors.get("email").asString(), "the email should have its code");
        assertEquals("PASSWORD_REQUIRED", errors.get("password").asString(), "the password should have its code");
        assertEquals("{\"errors\":{\"email\":\"EMAIL_REQUIRED\"}}", emailOnly.body(), "only the email should be listed");
        assertEquals("{\"errors\":{\"password\":\"PASSWORD_REQUIRED\"}}", passwordOnly.body(), "only the password should be listed");
    }

    @Test
    void submittedPasswordsAreNeverLoggedOrReturned(CapturedOutput output) throws Exception {
        LoggerFactory.getLogger(getClass()).info("output capture is working");
        String wrongPassword = "Wrong-pw-1";
        createVisitor(PASSWORD);

        HttpResponse<String> accepted = authenticate(EMAIL, PASSWORD);
        HttpResponse<String> rejected = authenticate(EMAIL, wrongPassword);
        HttpResponse<String> unknown = authenticate("nobody@example.com", wrongPassword);
        HttpResponse<String> invalid = authenticate("", wrongPassword);

        assertEquals(200, accepted.statusCode(), "the first attempt should be accepted");
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("Spring's debug logging should be on, or this test proves nothing")
                .contains("POST \"/api/authentications\"");
        assertThat(output.getAll()).as("the correct password should not be logged").doesNotContain(PASSWORD);
        assertThat(output.getAll()).as("a wrong password should not be logged").doesNotContain(wrongPassword);
        assertThat(accepted.body()).as("the accepted response should not contain the password").doesNotContain(PASSWORD);
        assertThat(rejected.body()).as("the rejected response should not contain the password").doesNotContain(wrongPassword);
        assertThat(unknown.body()).as("the unknown email response should not contain the password").doesNotContain(wrongPassword);
        assertThat(invalid.body()).as("the empty email response should not contain the password").doesNotContain(wrongPassword);
    }
}
