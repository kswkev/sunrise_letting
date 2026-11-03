package com.sunriseletting.identity.authentication;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sunriseletting.identity.account.Role;
import com.sunriseletting.identity.validation.AccountFieldError;

@RestController
public class AuthenticationController {

    private final AuthenticationService service;

    public AuthenticationController(AuthenticationService service) {
        this.service = service;
    }

    @PostMapping("/api/authentications")
    public ResponseEntity<?> authenticate(@RequestBody AuthenticationRequest request) {
        AuthenticationResult result = service.authenticate(request);
        if (result instanceof AuthenticationResult.Authenticated authenticated) {
            return ResponseEntity.ok(new Authenticated(authenticated.accountId(), authenticated.role()));
        }
        if (result instanceof AuthenticationResult.Invalid invalid) {
            Map<String, AccountFieldError> errors = new LinkedHashMap<>();
            invalid.errors().forEach((field, error) -> errors.put(field.apiName(), error));
            return ResponseEntity.badRequest().body(new FieldErrors(errors));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new NotAuthenticated("INVALID_CREDENTIALS"));
    }

    record Authenticated(UUID accountId, Role role) {
    }

    record FieldErrors(Map<String, AccountFieldError> errors) {
    }

    record NotAuthenticated(String error) {
    }
}
