package com.sunriseletting.identity.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AccountValidationController.class)
@Import({AccountFieldValidator.class, PasswordRules.class})
@ExtendWith(OutputCaptureExtension.class)
class AccountValidationControllerTest {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult post(String field, String json) {
        return mvc.post().uri("/api/account-validation/{field}", field)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    @ParameterizedTest
    @CsvSource({
            "title,      Mr",
            "firstName,  Jo",
            "surname,    Smith",
            "email,      jo@example.com",
            "phone,      07700 900123",
            "password,   Abcdefg1!",
            "titleOther, Rev",
    })
    void validValueGivesNullError(String field, String value) {
        MvcTestResult result = post(field, "{\"value\":\"" + value + "\"}");

        assertThat(result).as(field + " check should succeed").hasStatusOk();
        assertThat(result).as(field + " check should return JSON").hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().as("a valid " + field + " should give a null error").isLenientlyEqualTo("{\"error\":null}");
    }

    @ParameterizedTest
    @CsvSource({
            "title,      Sir,                        TITLE_INVALID",
            "email,      not-an-email,               EMAIL_INVALID",
            "phone,      0113-496-0000,              PHONE_INVALID_CHARACTERS",
            "phone,      +1 202 555 0100,            PHONE_INVALID",
            "password,   short,                      PASSWORD_TOO_SHORT",
    })
    void invalidValueGivesItsCode(String field, String value, String code) {
        MvcTestResult result = post(field, "{\"value\":\"" + value + "\"}");

        assertThat(result).as(field + " check should still be a 200").hasStatusOk();
        assertThat(result).bodyJson().as(value + " should give " + code)
                .isLenientlyEqualTo("{\"error\":\"" + code + "\"}");
    }

    @Test
    void confirmPasswordIsComparedWithThePasswordSent() {
        MvcTestResult matching = post("confirmPassword", "{\"value\":\"Abcdefg1!\",\"password\":\"Abcdefg1!\"}");
        MvcTestResult different = post("confirmPassword", "{\"value\":\"Abcdefg1!\",\"password\":\"Different1!\"}");

        assertThat(matching).bodyJson().as("matching passwords should give a null error")
                .isLenientlyEqualTo("{\"error\":null}");
        assertThat(different).bodyJson().as("different passwords should give PASSWORDS_DO_NOT_MATCH")
                .isLenientlyEqualTo("{\"error\":\"PASSWORDS_DO_NOT_MATCH\"}");
    }

    @Test
    void responseNeverContainsThePassword() {
        MvcTestResult result = post("password", "{\"value\":\"Secret-pw-1\"}");

        assertThat(result).bodyText().as("the response should not repeat the password")
                .doesNotContain("Secret-pw-1");
    }

    @Test
    void unknownFieldGivesNotFound() {
        assertThat(post("nickname", "{\"value\":\"Jo\"}")).as("an unknown field should give 404").hasStatus(404);
    }

    @Test
    void requestBodyIsNotLoggedEvenWhenItIsMalformed(CapturedOutput output) {
        // Jackson repeats an unquoted token in its error message, which Spring logs when it resolves the error.
        LoggerFactory.getLogger(getClass()).warn("output capture is working");

        MvcTestResult result = post("password", "{\"value\":SecretPw1Token}");

        assertThat(result).as("malformed JSON should give 400").hasStatus(400);
        assertThat(output.getOut()).as("the capture should see application logs").contains("output capture is working");
        assertThat(output.getOut()).as("a malformed body should not reach the logs").doesNotContain("SecretPw1Token");
    }
}
