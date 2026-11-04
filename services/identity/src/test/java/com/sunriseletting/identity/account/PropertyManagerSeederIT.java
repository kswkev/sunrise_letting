package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sunriseletting.identity.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Starts the whole service against PostgreSQL with two Property Manager accounts configured, one of them listed a
 * second time with different capitals, spaces and password. Each test that changes the table puts the seeded accounts
 * back afterwards, and the test of what start-up did runs first, before anything has been changed. The loggers that
 * could show a value being bound or stored are turned up, as a developer might to investigate a problem.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "identity.property-managers[0].email=" + PropertyManagerSeederIT.CONFIGURED_FIRST_EMAIL,
        "identity.property-managers[0].temporary-password=" + PropertyManagerSeederIT.FIRST_PASSWORD,
        "identity.property-managers[1].email=" + PropertyManagerSeederIT.SECOND_EMAIL,
        "identity.property-managers[1].temporary-password=" + PropertyManagerSeederIT.SECOND_PASSWORD,
        "identity.property-managers[2].email=" + PropertyManagerSeederIT.CONFIGURED_FIRST_EMAIL_AGAIN,
        "identity.property-managers[2].temporary-password=" + PropertyManagerSeederIT.REPEATED_ENTRY_PASSWORD,
        "logging.level.com.sunriseletting=DEBUG",
        "logging.level.org.springframework.jdbc=TRACE",
        "logging.level.org.springframework.boot.context.properties=TRACE"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertyManagerSeederIT {

    static final String CONFIGURED_FIRST_EMAIL = "Pat.Manager@Example.COM";
    // The backslash stops the inlined property's parser from dropping the leading space.
    static final String CONFIGURED_FIRST_EMAIL_AGAIN = "\\  PAT.manager@example.com ";
    static final String FIRST_PASSWORD = "temporary-first-Secret";
    static final String SECOND_EMAIL = "lee.manager@example.com";
    static final String SECOND_PASSWORD = "temporary-second-Secret";
    static final String REPEATED_ENTRY_PASSWORD = "temporary-repeat-Secret";

    private static final String FIRST_EMAIL = "pat.manager@example.com";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PropertyManagerSeeder seeder;

    private boolean tableChanged;

    @AfterEach
    void putTheSeededAccountsBack() {
        if (tableChanged) {
            jdbc.sql("delete from accounts").update();
            seeder.seed();
        }
    }

    private record StoredAccount(String id, String email, String title, String firstName, String surname, String phone,
            String passwordHash, String role, boolean passwordTemporary) {
    }

    private StoredAccount stored(String email) {
        return jdbc.sql("""
                        SELECT id, email, title, first_name, surname, phone, password_hash, role, password_temporary
                        FROM accounts WHERE email = :email
                        """)
                .param("email", email)
                .query(StoredAccount.class)
                .single();
    }

    private int accountCount() {
        return jdbc.sql("select count(*) from accounts").query(Integer.class).single();
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void insertVisitor(String email, String passwordHash) {
        jdbc.sql("""
                        INSERT INTO accounts (id, email, title, first_name, surname, phone, password_hash, role)
                        VALUES (:id, :email, 'Mr', 'Jo', 'Smith', '+447700900123', :hash, 'VISITOR')
                        """)
                .param("id", UUID.randomUUID())
                .param("email", email)
                .param("hash", passwordHash)
                .update();
    }

    @Test
    @Order(1)
    void startUpCreatesEachConfiguredAccountOnceAndLogsTheCountsButNoPassword(CapturedOutput output) {
        assertEquals(2, accountCount(), "the repeated entry should not create a third account");
        for (String[] account : List.of(new String[] {FIRST_EMAIL, FIRST_PASSWORD}, new String[] {SECOND_EMAIL, SECOND_PASSWORD})) {
            StoredAccount stored = stored(account[0]);
            assertEquals("PROPERTY_MANAGER", stored.role(), account[0] + " should have the Property Manager role only");
            assertTrue(stored.passwordTemporary(), account[0] + " should be marked as having a temporary password");
            assertNotEquals(account[1], stored.passwordHash(), account[0] + " should not store the password itself");
            assertTrue(passwordEncoder.matches(account[1], stored.passwordHash()),
                    account[0] + " should store a hash that matches the configured password");
            assertNull(stored.title(), account[0] + " should have no title");
            assertNull(stored.firstName(), account[0] + " should have no first name");
            assertNull(stored.surname(), account[0] + " should have no surname");
            assertNull(stored.phone(), account[0] + " should have no phone");
        }
        assertFalse(passwordEncoder.matches(REPEATED_ENTRY_PASSWORD, stored(FIRST_EMAIL).passwordHash()),
                "the repeated entry should not replace the first entry's password");

        assertThat(output.getAll()).as("start-up should log what it did").contains("2 created, 1 skipped");
        assertThat(output.getAll()).as("the first password should not be logged").doesNotContain(FIRST_PASSWORD);
        assertThat(output.getAll()).as("the second password should not be logged").doesNotContain(SECOND_PASSWORD);
        assertThat(output.getAll()).as("the repeated entry's password should not be logged").doesNotContain(REPEATED_ENTRY_PASSWORD);
    }

    @Test
    void seedingAgainCreatesNoDuplicatesAndChangesNothing() {
        StoredAccount before = stored(FIRST_EMAIL);

        seeder.seed();

        assertEquals(2, accountCount(), "seeding again should not add accounts");
        assertEquals(before, stored(FIRST_EMAIL), "seeding again should not touch an existing account");
    }

    @Test
    void aChangedPasswordSurvivesSeedingAgain() {
        tableChanged = true;
        String changedHash = passwordEncoder.encode("Changed-pw-1");
        jdbc.sql("UPDATE accounts SET password_hash = :hash, password_temporary = false WHERE email = :email")
                .param("hash", changedHash)
                .param("email", FIRST_EMAIL)
                .update();

        seeder.seed();

        StoredAccount after = stored(FIRST_EMAIL);
        assertEquals(changedHash, after.passwordHash(), "seeding again should not undo a changed password");
        assertFalse(after.passwordTemporary(), "seeding again should not make the password temporary again");
    }

    @Test
    void anEmailUsedByAVisitorLeavesThatAccountUnchangedAndTheOthersAreStillSeeded(CapturedOutput output) {
        tableChanged = true;
        jdbc.sql("delete from accounts").update();
        insertVisitor(FIRST_EMAIL, "the-visitors-hash");

        seeder.seed();

        StoredAccount visitor = stored(FIRST_EMAIL);
        assertEquals("VISITOR", visitor.role(), "the Visitor should keep their role");
        assertEquals("the-visitors-hash", visitor.passwordHash(), "the Visitor should keep their password");
        assertFalse(visitor.passwordTemporary(), "the Visitor should not get a temporary password");
        assertEquals("Jo", visitor.firstName(), "the Visitor should keep their details");
        assertEquals("PROPERTY_MANAGER", stored(SECOND_EMAIL).role(), "the next configured account should still be created");
        // Only the seeder's own lines: this class turns JDBC up to TRACE, which shows bound values, the email among them.
        List<String> clashes = output.getAll().lines().filter(line -> line.contains("Property Manager account not created")).toList();
        assertThat(clashes).as("the clash should be logged for each entry with that email, giving its position").hasSize(2);
        assertThat(clashes.get(0)).as("the first entry should be reported by its position")
                .contains("entry 0 has an email already used by a Visitor account, so it was not changed");
        assertThat(clashes.get(1)).as("the repeated entry should be reported by its position")
                .contains("entry 2 has an email already used by a Visitor account, so it was not changed");
        assertThat(clashes).as("the email is personal data and should not be logged").noneMatch(line -> line.toLowerCase().contains(FIRST_EMAIL));
    }

    @Test
    void aSeededPasswordAuthenticatesThroughTheApi() throws Exception {
        HttpResponse<String> response = post("/api/authentications", Map.of("email", FIRST_EMAIL, "password", FIRST_PASSWORD));

        assertEquals(200, response.statusCode(), "the temporary password should sign in through the existing API");
        JsonNode body = json.readTree(response.body());
        assertEquals(stored(FIRST_EMAIL).id(), body.get("accountId").asString(), "the response should give the account's id");
        assertEquals("PROPERTY_MANAGER", body.get("role").asString(), "the response should give the Property Manager role");
        assertTrue(body.get("temporaryPassword").asBoolean(), "the response should say the seeded password is temporary");
    }

    @Test
    void aVisitorCreatedThroughTheApiDoesNotHaveATemporaryPassword() throws Exception {
        tableChanged = true;
        HttpResponse<String> created = post("/api/accounts/visitors", Map.of(
                "title", "Mr",
                "firstName", "Jo",
                "surname", "Smith",
                "email", "jo@example.com",
                "phone", "07700 900123",
                "password", "Secret-pw-1",
                "confirmPassword", "Secret-pw-1"));

        assertEquals(201, created.statusCode(), "the Visitor should be created");
        assertFalse(stored("jo@example.com").passwordTemporary(), "a Visitor chooses their own password");
    }

    @Test
    void aVisitorStillNeedsTheProfileFieldsInTheDataStore() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO accounts (id, email, password_hash, role)
                        VALUES (:id, 'no-details@example.com', 'a-hash', 'VISITOR')
                        """)
                .param("id", UUID.randomUUID())
                .update())
                .as("only Property Manager accounts may leave the profile fields empty")
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
