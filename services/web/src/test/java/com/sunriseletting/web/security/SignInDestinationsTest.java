package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SignInDestinationsTest {

    @Test
    void visitorGoesToAValidReturnAddress() {
        assertEquals("/properties/123", SignInDestinations.forVisitor("/properties/123"),
                "a Visitor should go back to the page they came from");
    }

    @Test
    void visitorGoesToPropertiesWithoutAReturnAddress() {
        assertEquals("/properties", SignInDestinations.forVisitor(null), "with no return address a Visitor goes to the search page");
    }

    @Test
    void visitorGoesToPropertiesWithAnUnsafeReturnAddress() {
        assertEquals("/properties", SignInDestinations.forVisitor("//evil.example"), "an unsafe return address counts as none");
    }

    @Test
    void alreadySignedInVisitorGoesToProperties() {
        assertEquals("/properties", SignInDestinations.whenAlreadySignedIn(new SessionPrincipal("account-1", AccountRole.VISITOR)),
                "a signed-in Visitor goes to the search page, whatever the return address");
    }
}
