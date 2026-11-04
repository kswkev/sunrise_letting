package com.sunriseletting.identity.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks one sign-up field at a time, for inline validation. It never looks up whether an account exists.
 */
@RestController
public class AccountValidationController {

    private final AccountFieldValidator validator;

    public AccountValidationController(AccountFieldValidator validator) {
        this.validator = validator;
    }

    @PostMapping("/api/account-validation/{field}")
    public FieldValidationResponse validate(@PathVariable String field, @RequestBody FieldValidationRequest request) {
        AccountField accountField = AccountField.fromApiName(field)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new FieldValidationResponse(
                validator.validate(accountField, request.value(), request.password()).orElse(null));
    }
}
