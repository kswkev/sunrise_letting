package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
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

import com.sunriseletting.identity.validation.AccountFieldError;

@WebMvcTest(ChangePasswordController.class)
@ExtendWith(OutputCaptureExtension.class)
class ChangePasswordControllerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final ChangePasswordRequest REQUEST = new ChangePasswordRequest("New-secret-1", "New-secret-1");
    private static final String JSON = "{\"newPassword\":\"New-secret-1\",\"confirmNewPassword\":\"New-secret-1\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ChangePasswordService service;

    private MvcTestResult post(String accountId, String json) {
        return mvc.post().uri("/api/accounts/" + accountId + "/password").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void changedPasswordGivesNoContent() {
        when(service.change(ACCOUNT_ID, REQUEST)).thenReturn(new ChangePasswordResult.Changed());

        MvcTestResult result = post(ACCOUNT_ID.toString(), JSON);

        verify(service, description("the service should get the account and the submitted fields")).change(ACCOUNT_ID, REQUEST);
        assertThat(result).as("a change should give 204").hasStatus(204);
        assertThat(result).as("a 204 should have no body").body().isEmpty();
    }

    @Test
    void invalidFieldsGiveOneCodePerField() {
        when(service.change(ACCOUNT_ID, REQUEST)).thenReturn(new ChangePasswordResult.Invalid(Map.of(
                "newPassword", AccountFieldError.PASSWORD_SAME_AS_TEMPORARY,
                "confirmNewPassword", AccountFieldError.PASSWORDS_DO_NOT_MATCH)));

        MvcTestResult result = post(ACCOUNT_ID.toString(), JSON);

        verify(service, description("the service should get the account and the submitted fields")).change(ACCOUNT_ID, REQUEST);
        assertThat(result).as("invalid fields should give 400").hasStatus(400);
        assertThat(result).bodyJson().as("the response should have one code per failing field")
                .isStrictlyEqualTo("""
                        {"errors":{"newPassword":"PASSWORD_SAME_AS_TEMPORARY","confirmNewPassword":"PASSWORDS_DO_NOT_MATCH"}}
                        """);
    }

    @Test
    void unknownAccountGivesNotFound() {
        when(service.change(ACCOUNT_ID, REQUEST)).thenReturn(new ChangePasswordResult.AccountNotFound());

        MvcTestResult result = post(ACCOUNT_ID.toString(), JSON);

        verify(service, description("the service should get the account and the submitted fields")).change(ACCOUNT_ID, REQUEST);
        assertThat(result).as("an unknown account should give 404").hasStatus(404);
        assertThat(result).bodyJson().as("the response should give the code")
                .isStrictlyEqualTo("{\"error\":\"ACCOUNT_NOT_FOUND\"}");
    }

    @Test
    void accountWithoutATemporaryPasswordGivesConflict() {
        when(service.change(ACCOUNT_ID, REQUEST)).thenReturn(new ChangePasswordResult.NoTemporaryPassword());

        MvcTestResult result = post(ACCOUNT_ID.toString(), JSON);

        verify(service, description("the service should get the account and the submitted fields")).change(ACCOUNT_ID, REQUEST);
        assertThat(result).as("no temporary password should give 409").hasStatus(409);
        assertThat(result).bodyJson().as("the response should give the code")
                .isStrictlyEqualTo("{\"error\":\"NO_TEMPORARY_PASSWORD\"}");
    }

    @Test
    void accountIdThatIsNotAUuidGivesBadRequest() {
        MvcTestResult result = post("not-a-uuid", JSON);

        assertThat(result).as("an id that isn't a UUID should give 400").hasStatus(400);
    }

    @Test
    void malformedBodyIsNotLogged(CapturedOutput output) {
        // Jackson repeats an unquoted token in its error message, which Spring logs when it resolves the error.
        LoggerFactory.getLogger(getClass()).warn("output capture is working");

        MvcTestResult result = post(ACCOUNT_ID.toString(), "{\"newPassword\":SecretPw1Token}");

        assertThat(result).as("malformed JSON should give 400").hasStatus(400);
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("a malformed body should not reach the logs").doesNotContain("SecretPw1Token");
    }
}
