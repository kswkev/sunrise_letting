package com.sunriseletting.identity.rememberme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sunriseletting.identity.account.Role;

@WebMvcTest(RememberMeTokenController.class)
@ExtendWith(OutputCaptureExtension.class)
class RememberMeTokenControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("7f8a3c52-6a51-4c7f-9d7d-0d2a1f5f3b11");
    private static final Instant EXPIRES_AT = Instant.parse("2026-12-03T10:15:30Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RememberMeTokenService service;

    private MvcTestResult post(String path, String json) {
        return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult issue() {
        return post("/api/remember-me-tokens", "{\"accountId\":\"" + ACCOUNT_ID + "\"}");
    }

    private MvcTestResult verifyToken() {
        return post("/api/remember-me-tokens/verify", "{\"series\":\"the-series\",\"token\":\"the-token\"}");
    }

    @Test
    void issuedTokenGivesItsSeriesTokenAndExpiry() {
        when(service.issue(ACCOUNT_ID)).thenReturn(Optional.of(new IssuedRememberMeToken("the-series", "the-token", EXPIRES_AT)));

        MvcTestResult result = issue();

        verify(service, description("the service should get the account id")).issue(ACCOUNT_ID);
        assertThat(result).as("an issued token should give 201").hasStatus(201);
        assertThat(result).bodyJson().as("the response should give the series, token and expiry").isStrictlyEqualTo("""
                {"series":"the-series","token":"the-token","expiresAt":"2026-12-03T10:15:30Z"}
                """);
    }

    @Test
    void refusedAccountGivesUnprocessableEntityWithItsCode() {
        when(service.issue(ACCOUNT_ID)).thenReturn(Optional.empty());

        MvcTestResult result = issue();

        verify(service, description("the service should get the account id")).issue(ACCOUNT_ID);
        assertThat(result).as("an account that can't have a token should give 422").hasStatus(422);
        assertThat(result).bodyJson().as("the response should give the code")
                .isStrictlyEqualTo("{\"error\":\"REMEMBER_ME_NOT_ALLOWED\"}");
    }

    @Test
    void accountIdThatIsNotAUuidGivesBadRequest() {
        MvcTestResult result = post("/api/remember-me-tokens", "{\"accountId\":\"not-a-uuid\"}");

        assertThat(result).as("an account id that isn't a UUID should give 400").hasStatus(400);
    }

    @Test
    void verifiedTokenGivesTheAccountRoleNewTokenAndExpiry() {
        when(service.verify("the-series", "the-token"))
                .thenReturn(Optional.of(new VerifiedRememberMeToken(ACCOUNT_ID, Role.VISITOR, "the-new-token", EXPIRES_AT)));

        MvcTestResult result = verifyToken();

        verify(service, description("the service should get the series and token")).verify("the-series", "the-token");
        assertThat(result).as("a valid token should give 200").hasStatusOk();
        assertThat(result).bodyJson().as("the response should give the account, role, new token and expiry").isStrictlyEqualTo("""
                {"accountId":"%s","role":"VISITOR","token":"the-new-token","expiresAt":"2026-12-03T10:15:30Z"}
                """.formatted(ACCOUNT_ID));
    }

    @Test
    void rejectedTokenGivesUnauthorizedWithItsCode() {
        when(service.verify("the-series", "the-token")).thenReturn(Optional.empty());

        MvcTestResult result = verifyToken();

        verify(service, description("the service should get the series and token")).verify("the-series", "the-token");
        assertThat(result).as("a rejected token should give 401").hasStatus(401);
        assertThat(result).bodyJson().as("the response should not say why it was rejected")
                .isStrictlyEqualTo("{\"error\":\"INVALID_REMEMBER_ME_TOKEN\"}");
    }

    @Test
    void deleteRevokesTheSeriesAndGivesNoContent() {
        MvcTestResult result = mvc.delete().uri("/api/remember-me-tokens/{series}", "the-series").exchange();

        verify(service, description("the service should get the series")).revoke("the-series");
        assertThat(result).as("a revoke should give 204").hasStatus(204);
    }

    @Test
    void malformedBodyIsNotLogged(CapturedOutput output) {
        // Jackson repeats an unquoted token in its error message, which Spring logs when it resolves the error.
        LoggerFactory.getLogger(getClass()).warn("output capture is working");

        MvcTestResult result = post("/api/remember-me-tokens/verify", "{\"series\":\"s\",\"token\":SecretRawToken}");

        assertThat(result).as("malformed JSON should give 400").hasStatus(400);
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("a malformed body should not reach the logs").doesNotContain("SecretRawToken");
    }
}
