package com.sunriseletting.identity.authentication;

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

import com.sunriseletting.identity.account.Role;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;

@WebMvcTest(AuthenticationController.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationControllerTest {

    private static final AuthenticationRequest REQUEST = new AuthenticationRequest("jo@example.com", "Secret-pw-1");
    private static final String JSON = "{\"email\":\"jo@example.com\",\"password\":\"Secret-pw-1\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthenticationService service;

    private MvcTestResult post(String json) {
        return mvc.post().uri("/api/authentications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void matchGivesTheAccountIdAndRoleOnly() {
        UUID id = UUID.randomUUID();
        when(service.authenticate(REQUEST)).thenReturn(new AuthenticationResult.Authenticated(id, Role.VISITOR));

        MvcTestResult result = post(JSON);

        verify(service, description("the service should get the submitted details")).authenticate(REQUEST);
        assertThat(result).as("a match should give 200").hasStatusOk();
        assertThat(result).bodyJson().as("the response should give only the id and role")
                .isStrictlyEqualTo("{\"accountId\":\"" + id + "\",\"role\":\"VISITOR\"}");
    }

    @Test
    void emptyFieldsGiveOneCodePerField() {
        when(service.authenticate(REQUEST)).thenReturn(new AuthenticationResult.Invalid(Map.of(
                AccountField.EMAIL, AccountFieldError.EMAIL_REQUIRED,
                AccountField.PASSWORD, AccountFieldError.PASSWORD_REQUIRED)));

        MvcTestResult result = post(JSON);

        verify(service, description("the service should get the submitted details")).authenticate(REQUEST);
        assertThat(result).as("empty fields should give 400").hasStatus(400);
        assertThat(result).bodyJson().as("the response should list the empty fields")
                .isStrictlyEqualTo("{\"errors\":{\"email\":\"EMAIL_REQUIRED\",\"password\":\"PASSWORD_REQUIRED\"}}");
    }

    @Test
    void invalidCredentialsGiveAGenericResponse() {
        when(service.authenticate(REQUEST)).thenReturn(new AuthenticationResult.InvalidCredentials());

        MvcTestResult result = post(JSON);

        verify(service, description("the service should get the submitted details")).authenticate(REQUEST);
        assertThat(result).as("invalid credentials should give 401").hasStatus(401);
        assertThat(result).bodyJson().as("the response should not say which part was wrong")
                .isStrictlyEqualTo("{\"error\":\"INVALID_CREDENTIALS\"}");
    }

    @Test
    void malformedBodyIsNotLogged(CapturedOutput output) {
        // Jackson repeats an unquoted token in its error message, which Spring logs when it resolves the error.
        LoggerFactory.getLogger(getClass()).warn("output capture is working");

        MvcTestResult result = post("{\"email\":\"jo@example.com\",\"password\":SecretPw1Token}");

        assertThat(result).as("malformed JSON should give 400").hasStatus(400);
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("a malformed body should not reach the logs").doesNotContain("SecretPw1Token");
    }
}
