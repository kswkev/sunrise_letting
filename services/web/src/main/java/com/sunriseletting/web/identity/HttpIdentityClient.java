package com.sunriseletting.web.identity;

import java.time.Instant;
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
                                    || !answer.errors().values().stream().allMatch(HttpIdentityClient::isSignInFieldCode)) {
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

    @Override
    public RememberMeTokenResult issueRememberMeToken(String accountId) {
        try {
            return restClient.post()
                    .uri("/api/remember-me-tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new IssueTokenBody(accountId))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.CREATED.value()) {
                            IssuedAnswer answer = response.bodyTo(IssuedAnswer.class);
                            if (answer == null || answer.series() == null || answer.token() == null || answer.expiresAt() == null) {
                                return RememberMeTokenResult.unavailable();
                            }
                            return new RememberMeTokenResult.Issued(answer.series(), answer.token(), answer.expiresAt());
                        }
                        if (status == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
                            return RememberMeTokenResult.notAllowed();
                        }
                        return unexpectedStatus(status, RememberMeTokenResult.unavailable());
                    });
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Identity issue remember-me token failed: {}", e.getClass().getSimpleName());
            return RememberMeTokenResult.unavailable();
        }
    }

    @Override
    public RememberMeVerifyResult verifyRememberMeToken(String series, String token) {
        try {
            return restClient.post()
                    .uri("/api/remember-me-tokens/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyTokenBody(series, token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.OK.value()) {
                            VerifiedAnswer answer = response.bodyTo(VerifiedAnswer.class);
                            if (answer == null || answer.accountId() == null || !isKnownRole(answer.role())
                                    || answer.token() == null || answer.expiresAt() == null) {
                                return RememberMeVerifyResult.unavailable();
                            }
                            return new RememberMeVerifyResult.Verified(answer.accountId(), AccountRole.valueOf(answer.role()), answer.token(), answer.expiresAt());
                        }
                        if (status == HttpStatus.UNAUTHORIZED.value()) {
                            return RememberMeVerifyResult.invalid();
                        }
                        return unexpectedStatus(status, RememberMeVerifyResult.unavailable());
                    });
        } catch (RestClientException | HttpMessageConversionException e) {
            log.warn("Identity verify remember-me token failed: {}", e.getClass().getSimpleName());
            return RememberMeVerifyResult.unavailable();
        }
    }

    @Override
    public void revokeRememberMeToken(String series) {
        try {
            restClient.delete()
                    .uri("/api/remember-me-tokens/{series}", series)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        return status == HttpStatus.NO_CONTENT.value() ? status : unexpectedStatus(status, status);
                    });
        } catch (RestClientException e) {
            log.warn("Identity revoke remember-me token failed: {}", e.getClass().getSimpleName());
        }
    }

    private static AuthenticationResult signedIn(CreatedAnswer answer) {
        if (answer == null || answer.accountId() == null || !isKnownRole(answer.role())) {
            return AuthenticationResult.unavailable();
        }
        return new AuthenticationResult.SignedIn(answer.accountId(), AccountRole.valueOf(answer.role()));
    }

    /** {@code Set.of} sets throw on {@code contains(null)}, and a field with no code can reach here in the JSON. */
    private static boolean isSignInFieldCode(String code) {
        return code != null && SIGN_IN_FIELD_CODES.contains(code);
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

    private record IssueTokenBody(String accountId) {
    }

    private record VerifyTokenBody(String series, String token) {

        @Override
        public String toString() {
            return "VerifyTokenBody[redacted]";
        }
    }

    private record IssuedAnswer(String series, String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedAnswer[redacted]";
        }
    }

    private record VerifiedAnswer(String accountId, String role, String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "VerifiedAnswer[redacted]";
        }
    }

    private record ValidationAnswer(String error) {
    }

    private record CreatedAnswer(String accountId, String role) {
    }

    private record ValidationFailedAnswer(Map<String, String> errors) {
    }
}
