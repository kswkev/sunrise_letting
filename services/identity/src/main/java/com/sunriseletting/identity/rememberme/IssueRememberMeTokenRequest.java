package com.sunriseletting.identity.rememberme;

import java.util.UUID;

public record IssueRememberMeTokenRequest(UUID accountId) {
}
