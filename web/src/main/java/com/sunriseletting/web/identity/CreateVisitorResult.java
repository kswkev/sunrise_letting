package com.sunriseletting.web.identity;

import java.util.Map;

/** What identity said about a request to create a Visitor account. */
public sealed interface CreateVisitorResult {

    record Created(String accountId, String role) implements CreateVisitorResult {
    }

    /** Fields that break a rule: identity's field name to its error code. */
    record ValidationFailed(Map<String, String> errors) implements CreateVisitorResult {
    }

    /** The email is already in use. This must not be shown to the user as such. */
    record NotCreated() implements CreateVisitorResult {
    }

    /** The request couldn't be completed: a connection error, a timeout, a 5xx or a response outside the contract. */
    record Unavailable() implements CreateVisitorResult {
    }

    static CreateVisitorResult notCreated() {
        return new NotCreated();
    }

    static CreateVisitorResult unavailable() {
        return new Unavailable();
    }
}
