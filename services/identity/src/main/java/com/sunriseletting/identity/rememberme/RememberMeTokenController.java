package com.sunriseletting.identity.rememberme;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RememberMeTokenController {

    private final RememberMeTokenService service;

    public RememberMeTokenController(RememberMeTokenService service) {
        this.service = service;
    }

    @PostMapping("/api/remember-me-tokens")
    public ResponseEntity<?> issue(@RequestBody IssueRememberMeTokenRequest request) {
        return service.issue(request.accountId())
                .<ResponseEntity<?>>map(issued -> ResponseEntity.status(HttpStatus.CREATED).body(issued))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Refused("REMEMBER_ME_NOT_ALLOWED")));
    }

    @PostMapping("/api/remember-me-tokens/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRememberMeTokenRequest request) {
        return service.verify(request.series(), request.token())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Refused("INVALID_REMEMBER_ME_TOKEN")));
    }

    @DeleteMapping("/api/remember-me-tokens/{series}")
    public ResponseEntity<Void> revoke(@PathVariable String series) {
        service.revoke(series);
        return ResponseEntity.noContent().build();
    }

    record Refused(String error) {
    }
}
