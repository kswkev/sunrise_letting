package com.sunriseletting.web.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sunriseletting.web.security.AccountRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class HttpIdentityClientTest {

    private static final String BASE_URL = "http://identity.test";

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final CreateVisitorRequest REQUEST = new CreateVisitorRequest("Other", "Prof", "Jo", "Bloggs",
            "jo@example.com", "07700 900982", SECRET, SECRET);

    private MockRestServiceServer server;

    private HttpIdentityClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpIdentityClient(builder.build());
    }

    @Test
    void validateFieldReturnsValidWhenThereIsNoErrorCode() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"value\":\"jo@example.com\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"error\":null}", MediaType.APPLICATION_JSON));

        FieldValidationResult result = client.validateField(AccountField.EMAIL, "jo@example.com");

        assertEquals(FieldValidationResult.valid(), result, "no error code should mean valid");
        server.verify();
    }

    @Test
    void validateFieldReturnsTheErrorCode() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andRespond(withSuccess("{\"error\":\"EMAIL_INVALID\"}", MediaType.APPLICATION_JSON));

        FieldValidationResult result = client.validateField(AccountField.EMAIL, "not-an-email");

        assertEquals(new FieldValidationResult.Invalid("EMAIL_INVALID"), result, "an error code should mean invalid with that code");
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(AccountField.class)
    void validateFieldUsesIdentitysFieldNames(AccountField field) {
        server.expect(requestTo(BASE_URL + "/api/account-validation/" + field.apiName()))
                .andRespond(withSuccess("{\"error\":null}", MediaType.APPLICATION_JSON));

        client.validateField(field, "value");

        server.verify();
    }

    @Test
    void identityFieldNamesAreTheAgreedOnes() {
        assertEquals("title", AccountField.TITLE.apiName(), "identity's name for Title");
        assertEquals("titleOther", AccountField.TITLE_OTHER.apiName(), "identity's name for Other title");
        assertEquals("firstName", AccountField.FIRST_NAME.apiName(), "identity's name for First name");
        assertEquals("surname", AccountField.SURNAME.apiName(), "identity's name for Surname");
        assertEquals("email", AccountField.EMAIL.apiName(), "identity's name for Email");
        assertEquals("phone", AccountField.PHONE.apiName(), "identity's name for Phone");
        assertEquals("password", AccountField.PASSWORD.apiName(), "identity's name for Password");
        assertEquals("confirmPassword", AccountField.CONFIRM_PASSWORD.apiName(), "identity's name for Confirm password");
    }

    @Test
    void validateConfirmPasswordSendsThePasswordToo() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/confirmPassword"))
                .andExpect(content().json("{\"value\":\"typed\",\"password\":\"other\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"error\":\"PASSWORDS_DO_NOT_MATCH\"}", MediaType.APPLICATION_JSON));

        FieldValidationResult result = client.validateField(AccountField.CONFIRM_PASSWORD, "typed", "other");

        assertEquals(new FieldValidationResult.Invalid("PASSWORDS_DO_NOT_MATCH"), result, "the code should come back");
        server.verify();
    }

    @Test
    void validateFieldIsUnavailableOn500() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(FieldValidationResult.unavailable(), client.validateField(AccountField.EMAIL, "x"),
                "a 500 means the check can't be done");
    }

    @Test
    void validateFieldIsUnavailableOnAnyOtherStatus() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertEquals(FieldValidationResult.unavailable(), client.validateField(AccountField.EMAIL, "x"),
                "a status outside the contract means the check can't be done");
    }

    @Test
    void validateFieldIsUnavailableWhenTheBodyCantBeRead() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertEquals(FieldValidationResult.unavailable(), client.validateField(AccountField.EMAIL, "x"),
                "an unreadable body means the check can't be done");
    }

    @Test
    void validateFieldIsUnavailableOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/account-validation/email"))
                .andRespond(withException(new IOException("Connection refused")));

        assertEquals(FieldValidationResult.unavailable(), client.validateField(AccountField.EMAIL, "x"),
                "a connection error means the check can't be done");
    }

    @Test
    void createVisitorSendsEveryFieldAndReturnsCreated() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"title":"Other","titleOther":"Prof","firstName":"Jo","surname":"Bloggs","email":"jo@example.com",
                         "phone":"07700 900982","password":"%s","confirmPassword":"%s"}
                        """.formatted(SECRET, SECRET), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accountId\":\"8f14e45f-ceea-467f-a8b2-1d2f6a7b9c10\",\"role\":\"VISITOR\"}"));

        CreateVisitorResult result = client.createVisitor(REQUEST);

        assertEquals(new CreateVisitorResult.Created("8f14e45f-ceea-467f-a8b2-1d2f6a7b9c10", "VISITOR"), result,
                "a 201 should give the new account's id and role");
        server.verify();
    }

    @Test
    void createVisitorReturnsTheFieldCodesOn400() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":{\"firstName\":\"FIRST_NAME_REQUIRED\",\"email\":\"EMAIL_INVALID\"}}"));

        CreateVisitorResult result = client.createVisitor(REQUEST);

        assertEquals(new CreateVisitorResult.ValidationFailed(Map.of("firstName", "FIRST_NAME_REQUIRED", "email", "EMAIL_INVALID")),
                result, "a 400 should give the field codes");
    }

    @Test
    void createVisitorIsUnavailableOnA400WithoutFieldCodes() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"status\":400}"));

        assertEquals(CreateVisitorResult.unavailable(), client.createVisitor(REQUEST),
                "a 400 that names no field is Spring's default error, not a validation result");
    }

    @Test
    void createVisitorReturnsNotCreatedOn409() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"ACCOUNT_NOT_CREATED\"}"));

        assertEquals(CreateVisitorResult.notCreated(), client.createVisitor(REQUEST), "a 409 means the email is already in use");
    }

    @Test
    void createVisitorIsUnavailableOn500() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(CreateVisitorResult.unavailable(), client.createVisitor(REQUEST), "a 500 is the server-error state");
    }

    @Test
    void createVisitorIsUnavailableOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withException(new IOException("Connection refused")));

        assertEquals(CreateVisitorResult.unavailable(), client.createVisitor(REQUEST), "a connection error is the server-error state");
    }

    @Test
    void authenticateSendsTheDetailsAsTypedAndReturnsTheAccount() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"email\":\" Jo@Example.com \",\"password\":\"%s\"}".formatted(SECRET), JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"accountId\":\"account-1\",\"role\":\"VISITOR\"}", MediaType.APPLICATION_JSON));

        AuthenticationResult result = client.authenticate(" Jo@Example.com ", SECRET);

        assertEquals(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR), result, "a 200 should give the account's id and role");
        server.verify();
    }

    @Test
    void authenticateAcceptsAPropertyManager() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withSuccess("{\"accountId\":\"account-2\",\"role\":\"PROPERTY_MANAGER\"}", MediaType.APPLICATION_JSON));

        assertEquals(new AuthenticationResult.SignedIn("account-2", AccountRole.PROPERTY_MANAGER), client.authenticate("pm@example.com", SECRET),
                "identity can answer with a Property Manager on this shared page");
    }

    @Test
    void authenticateIsUnavailableForARoleOutsideTheContract() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withSuccess("{\"accountId\":\"account-2\",\"role\":\"ADMIN\"}", MediaType.APPLICATION_JSON));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a@example.com", SECRET), "an unknown role is outside the contract");
    }

    @Test
    void authenticateIsUnavailableWhenTheAccountIsMissingFromA200() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withSuccess("{\"role\":\"VISITOR\"}", MediaType.APPLICATION_JSON));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a@example.com", SECRET), "a 200 with no account is outside the contract");
    }

    @Test
    void authenticateReturnsTheFieldCodesOn400() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":{\"email\":\"EMAIL_REQUIRED\",\"password\":\"PASSWORD_REQUIRED\"}}"));

        assertEquals(new AuthenticationResult.FieldErrors(Map.of("email", "EMAIL_REQUIRED", "password", "PASSWORD_REQUIRED")),
                client.authenticate("", ""), "a 400 should give the field codes");
    }

    @Test
    void authenticateIsUnavailableOnA400WithoutFieldCodes() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"status\":400}"));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a@example.com", SECRET),
                "a 400 that names no field is Spring's default error");
    }

    @Test
    void authenticateIsUnavailableForAFieldCodeOutsideTheContract() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":{\"email\":\"EMAIL_INVALID\"}}"));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a", SECRET), "the sign-in page has no copy for any other code");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"errors\":{\"email\":null}}", "{\"errors\":{\"email\":\"EMAIL_REQUIRED\",\"password\":null}}" })
    void authenticateIsUnavailableForANullFieldCode(String body) {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a", SECRET), "a field with no code is outside the contract");
        server.verify();
    }

    @Test
    void authenticateReturnsInvalidCredentialsOn401() {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"INVALID_CREDENTIALS\"}"));

        assertEquals(AuthenticationResult.invalidCredentials(), client.authenticate("a@example.com", SECRET), "a 401 means wrong details");
    }

    @Test
    void authenticateIsUnavailableOn500() {
        server.expect(requestTo(BASE_URL + "/api/authentications")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a@example.com", SECRET), "a 500 is the server-error state");
    }

    @Test
    void authenticateIsUnavailableOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/authentications")).andRespond(withException(new IOException("Connection refused")));

        assertEquals(AuthenticationResult.unavailable(), client.authenticate("a@example.com", SECRET), "a connection error is the server-error state");
    }

    @Test
    void passwordIsNotLoggedWhenAuthenticating(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/authentications"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(SECRET));

        client.authenticate("a@example.com", SECRET);

        assertFalse(output.getAll().contains(SECRET), "the password should never be logged");
    }

    @Test
    void issueRememberMeTokenSendsTheAccountAndReturnsTheToken() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"accountId\":\"account-1\"}", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"series\":\"s1\",\"token\":\"t1\",\"expiresAt\":\"2026-12-03T10:15:30Z\"}"));

        RememberMeTokenResult result = client.issueRememberMeToken("account-1");

        assertEquals(new RememberMeTokenResult.Issued("s1", "t1", Instant.parse("2026-12-03T10:15:30Z")), result, "a 201 should give the token");
        server.verify();
    }

    @Test
    void issueRememberMeTokenIsNotAllowedOn422() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"REMEMBER_ME_NOT_ALLOWED\"}"));

        assertEquals(RememberMeTokenResult.notAllowed(), client.issueRememberMeToken("account-1"), "a 422 means no token for this account");
    }

    @Test
    void issueRememberMeTokenIsUnavailableOutsideTheContract() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"series\":\"s1\"}"));

        assertEquals(RememberMeTokenResult.unavailable(), client.issueRememberMeToken("account-1"), "a 201 with no token is outside the contract");
    }

    @Test
    void issueRememberMeTokenIsUnavailableOnABadDate() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"series\":\"s1\",\"token\":\"t1\",\"expiresAt\":\"soon\"}"));

        assertEquals(RememberMeTokenResult.unavailable(), client.issueRememberMeToken("account-1"), "a date that can't be read is outside the contract");
    }

    @Test
    void issueRememberMeTokenIsUnavailableOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens")).andRespond(withException(new IOException("Connection refused")));

        assertEquals(RememberMeTokenResult.unavailable(), client.issueRememberMeToken("account-1"), "a connection error means no token");
    }

    @Test
    void verifyRememberMeTokenSendsTheCookieValuesAndReturnsTheNewToken() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"series\":\"s1\",\"token\":\"t1\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"accountId\":\"account-1\",\"role\":\"VISITOR\",\"token\":\"t2\",\"expiresAt\":\"2026-12-03T10:15:30Z\"}",
                        MediaType.APPLICATION_JSON));

        RememberMeVerifyResult result = client.verifyRememberMeToken("s1", "t1");

        assertEquals(new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, "t2", Instant.parse("2026-12-03T10:15:30Z")), result,
                "a 200 should give the account and the new token");
        server.verify();
    }

    @Test
    void verifyRememberMeTokenIsInvalidOn401() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"INVALID_REMEMBER_ME_TOKEN\"}"));

        assertEquals(RememberMeVerifyResult.invalid(), client.verifyRememberMeToken("s1", "t1"), "a 401 means the cookie is no good");
    }

    @Test
    void verifyRememberMeTokenIsUnavailableOn500() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(RememberMeVerifyResult.unavailable(), client.verifyRememberMeToken("s1", "t1"), "a 500 isn't a verdict on the cookie");
    }

    @Test
    void verifyRememberMeTokenIsUnavailableOutsideTheContract() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify"))
                .andRespond(withSuccess("{\"accountId\":\"account-1\",\"role\":\"ADMIN\",\"token\":\"t2\",\"expiresAt\":\"2026-12-03T10:15:30Z\"}", MediaType.APPLICATION_JSON));

        assertEquals(RememberMeVerifyResult.unavailable(), client.verifyRememberMeToken("s1", "t1"), "an unknown role is outside the contract");
    }

    @Test
    void verifyRememberMeTokenIsUnavailableOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify")).andRespond(withException(new IOException("Read timed out")));

        assertEquals(RememberMeVerifyResult.unavailable(), client.verifyRememberMeToken("s1", "t1"), "a timeout isn't a verdict on the cookie");
    }

    @Test
    void revokeRememberMeTokenDeletesTheSeries() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/s1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.revokeRememberMeToken("s1");

        server.verify();
    }

    @Test
    void revokeRememberMeTokenDoesNotThrowWhenIdentityFails() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/s1")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertDoesNotThrow(() -> client.revokeRememberMeToken("s1"), "signing out carries on whatever identity says");
    }

    @Test
    void revokeRememberMeTokenDoesNotThrowOnAConnectionError() {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/s1")).andRespond(withException(new IOException("Connection refused")));

        assertDoesNotThrow(() -> client.revokeRememberMeToken("s1"), "signing out carries on whatever happens to the connection");
    }

    @Test
    void rememberMeTokensAreNotInToStringOrTheLog(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/remember-me-tokens/verify"))
                .andRespond(withSuccess("{\"accountId\":\"account-1\",\"role\":\"VISITOR\",\"token\":\"new-token-value\",\"expiresAt\":\"2026-12-03T10:15:30Z\"}",
                        MediaType.APPLICATION_JSON));

        RememberMeVerifyResult result = client.verifyRememberMeToken("s1", "old-token-value");

        assertFalse(result.toString().contains("new-token-value"), "a result that reaches a log line must not carry the token");
        assertFalse(new RememberMeTokenResult.Issued("s1", "new-token-value", Instant.EPOCH).toString().contains("new-token-value"),
                "a result that reaches a log line must not carry the token");
        assertFalse(output.getAll().contains("new-token-value"), "the new token should never be logged");
        assertFalse(output.getAll().contains("old-token-value"), "the old token should never be logged");
    }

    @Test
    void requestToStringLeavesOutThePasswords() {
        assertFalse(REQUEST.toString().contains(SECRET), "a request that reaches a log line must not carry the passwords");
    }

    /** Turns request and converter logging all the way up, which is the worst a developer could do. */
    private Level previousLevel;

    private final Logger springWeb = (Logger) LoggerFactory.getLogger("org.springframework.web");

    @BeforeEach
    void turnUpLogging() {
        previousLevel = springWeb.getLevel();
        springWeb.setLevel(Level.TRACE);
    }

    @AfterEach
    void restoreLogging() {
        springWeb.setLevel(previousLevel);
    }

    @Test
    void passwordIsNotLoggedWhenCreatingAVisitor(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":{\"password\":\"PASSWORD_TOO_SHORT\"}}"));

        client.createVisitor(REQUEST);

        assertFalse(output.getAll().contains(SECRET), "the password should never be logged");
    }

    @Test
    void passwordIsNotLoggedWhenValidating(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/account-validation/confirmPassword"))
                .andRespond(withSuccess("{\"error\":null}", MediaType.APPLICATION_JSON));

        client.validateField(AccountField.CONFIRM_PASSWORD, SECRET, SECRET);

        assertFalse(output.getAll().contains(SECRET), "the password should never be logged");
    }

    @Test
    void passwordIsNotLoggedWhenTheCallFails(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/accounts/visitors"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(SECRET));

        client.createVisitor(REQUEST);

        assertFalse(output.getAll().contains(SECRET), "the password should never be logged");
    }
}
