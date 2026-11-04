package com.sunriseletting.identity.account;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sunriseletting.identity.validation.AccountDetails;
import com.sunriseletting.identity.validation.AccountFieldError;

@RestController
public class VisitorAccountController {

    private final VisitorAccountService service;

    public VisitorAccountController(VisitorAccountService service) {
        this.service = service;
    }

    @PostMapping("/api/accounts/visitors")
    public ResponseEntity<?> create(@RequestBody AccountDetails details) {
        CreateVisitorAccountResult result = service.create(details);
        if (result instanceof CreateVisitorAccountResult.Created created) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new AccountCreated(created.accountId(), created.role()));
        }
        if (result instanceof CreateVisitorAccountResult.Invalid invalid) {
            Map<String, AccountFieldError> errors = new LinkedHashMap<>();
            invalid.errors().forEach((field, error) -> errors.put(field.apiName(), error));
            return ResponseEntity.badRequest().body(new FieldErrors(errors));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new AccountNotCreated("ACCOUNT_NOT_CREATED"));
    }

    record AccountCreated(UUID accountId, Role role) {
    }

    record FieldErrors(Map<String, AccountFieldError> errors) {
    }

    record AccountNotCreated(String error) {
    }
}
