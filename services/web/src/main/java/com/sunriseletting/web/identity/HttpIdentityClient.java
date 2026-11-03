package com.sunriseletting.web.identity;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sunriseletting.web.security.AccountRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls identity over HTTP. Request and response bodies carry passwords and personal details, so
 * nothing here logs them, and failures log only the type of the problem, not its message.
 */
class HttpIdentityClient implements IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(HttpIdentityClient.class);

    private static final Set<String> SIGN_IN_FIELD_CODES = Set.of("EMAIL_REQUIRED", "PASSWORD_REQUIRED");

    private final RestClient restClient;

    HttpIdentityClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public FieldValidationResult validateField(AccountField field, String value) {
        return validateField(field, value, null);
    }

    @Override
    public FieldValidationResult validateField(AccountField field, String value, String password) {
        try {
            return restClient.post()
                    .uri("/api/account-validation/{field}", field.apiName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ValidationBody(value, password))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
                            return unexpectedStatus(response.getStatusCode().value(), FieldValidationResult.unavailable());
                        }
                        ValidationAnswer answer = response.bodyTo(ValidationAnswer.class);
                        if (answer == null) {
                            return FieldValidationResult.unavailable();
                        }
                        return answer.error() == null ? FieldValidationResult.valid()
                                : new FieldValidationResult.Invalid(answer.error());
                    });
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Identity field validation failed: {}", e.getClass().getSimpleName());
            return FieldValidationResult.unavailable();
        }
    }

    @Override
    public CreateVisitorResult createVisitor(CreateVisitorRequest body) {
        try {
            return restClient.post()
                    .uri("/api/accounts/visitors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.CREATED.value()) {
                            CreatedAnswer answer = response.bodyTo(CreatedAnswer.class);
                            if (answer == null || answer.accountId() == null || answer.role() == null) {
                                return CreateVisitorResult.unavailable();
                            }
                            return new CreateVisitorResult.Created(answer.accountId(), answer.role());
                        }
                        if (status == HttpStatus.BAD_REQUEST.value()) {
                            ValidationFailedAnswer answer = response.bodyTo(ValidationFailedAnswer.class);
                            if (answer == null || answer.errors() == null || answer.errors().isEmpty()) {
                                return unexpectedStatus(status, CreateVisitorResult.unavailable());
                            }
                            return new CreateVisitorResult.ValidationFailed(answer.errors());
                        }
                        if (status == HttpStatus.CONFLICT.value()) {
                            return CreateVisitorResult.notCreated();
                        }
                        return unexpectedStatus(status, CreateVisitorResult.unavailable());
                    });
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Identity create Visitor failed: {}", e.getClass().getSimpleName());
            return CreateVisitorResult.unavailable();
        }
    }

    @Override
    public AuthenticationResult authenticate(String email, String password) {
        try {
            return restClient.post()
                    .uri("/api/authentications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AuthenticationBody(email, password))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.OK.value()) {
                            return signedIn(response.bodyTo(CreatedAnswer.class));
                        }
                        if (status == HttpStatus.BAD_REQUEST.value()) {
                            ValidationFailedAnswer answer = response.bodyTo(ValidationFailedAnswer.class);
                            if (answer == null || answer.errors() == null || answer.errors().isEmpty()
                                    || !SIGN_IN_FIELD_CODES.containsAll(answer.errors().values())) {
                                return unexpectedStatus(status, AuthenticationResult.unavailable());
                            }
                            return new AuthenticationResult.FieldErrors(answer.errors());
                        }
                        if (status == HttpStatus.UNAUTHORIZED.value()) {
                            return AuthenticationResult.invalidCredentials();
                        }
                        return unexpectedStatus(status, AuthenticationResult.unavailable());
                    });
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Identity authentication failed: {}", e.getClass().getSimpleName());
            return AuthenticationResult.unavailable();
        }
    }

    private static AuthenticationResult signedIn(CreatedAnswer answer) {
        if (answer == null || answer.accountId() == null || !isKnownRole(answer.role())) {
            return AuthenticationResult.unavailable();
        }
        return new AuthenticationResult.SignedIn(answer.accountId(), AccountRole.valueOf(answer.role()));
    }

    private static boolean isKnownRole(String role) {
        return Arrays.stream(AccountRole.values()).anyMatch(known -> known.name().equals(role));
    }

    private static <T> T unexpectedStatus(int status, T result) {
        log.warn("Identity answered with an unexpected status: {}", status);
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ValidationBody(String value, String password) {

        @Override
        public String toString() {
            return "ValidationBody[redacted]";
        }
    }

    private record AuthenticationBody(String email, String password) {

        @Override
        public String toString() {
            return "AuthenticationBody[redacted]";
        }
    }

    private record ValidationAnswer(String error) {
    }

    private record CreatedAnswer(String accountId, String role) {
    }

    private record ValidationFailedAnswer(Map<String, String> errors) {
    }
}
