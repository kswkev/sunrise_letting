package com.sunriseletting.identity.account;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sunriseletting.identity.validation.AccountFieldError;

@RestController
public class ChangePasswordController {

    private final ChangePasswordService service;

    public ChangePasswordController(ChangePasswordService service) {
        this.service = service;
    }

    @PostMapping("/api/accounts/{accountId}/password")
    public ResponseEntity<?> change(@PathVariable UUID accountId, @RequestBody ChangePasswordRequest request) {
        ChangePasswordResult result = service.change(accountId, request);
        if (result instanceof ChangePasswordResult.Changed) {
            return ResponseEntity.noContent().build();
        }
        if (result instanceof ChangePasswordResult.Invalid invalid) {
            return ResponseEntity.badRequest().body(new FieldErrors(invalid.errors()));
        }
        if (result instanceof ChangePasswordResult.AccountNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new PasswordNotChanged("ACCOUNT_NOT_FOUND"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PasswordNotChanged("NO_TEMPORARY_PASSWORD"));
    }

    record FieldErrors(Map<String, AccountFieldError> errors) {
    }

    record PasswordNotChanged(String error) {
    }
}
