package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SignInDestinationsTest {

    private static final SessionPrincipal MANAGER = new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false);

    private static final SessionPrincipal TEMPORARY_MANAGER = new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, true);

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
        assertEquals("/properties", SignInDestinations.whenAlreadySignedIn(new SessionPrincipal("account-1", AccountRole.VISITOR, false)),
                "a signed-in Visitor goes to the search page, whatever the return address");
    }

    @Test
    void alreadySignedInPropertyManagerWithATemporaryPasswordGoesToChangeIt() {
        assertEquals("/manage/change-password", SignInDestinations.whenAlreadySignedIn(TEMPORARY_MANAGER),
                "a temporary password has to be changed first");
    }

    @Test
    void alreadySignedInPropertyManagerWithAPermanentPasswordGoesToTheManagerArea() {
        assertEquals("/manage", SignInDestinations.whenAlreadySignedIn(MANAGER), "a Property Manager's home is /manage");
    }

    @Test
    void afterSignInAVisitorGoesToTheReturnAddress() {
        assertEquals("/properties/123", SignInDestinations.afterSignIn(new SessionPrincipal("account-1", AccountRole.VISITOR, false), "/properties/123"),
                "a Visitor should go back to the page they came from");
    }

    @Test
    void afterSignInAPropertyManagerWithATemporaryPasswordGoesToChangeItWhateverTheReturnAddress() {
        assertEquals("/manage/change-password", SignInDestinations.afterSignIn(TEMPORARY_MANAGER, "/properties/123"),
                "a temporary password has to be changed first, whatever the return address");
    }

    @Test
    void afterSignInAPropertyManagerWithAPermanentPasswordGoesToTheManagerAreaWhateverTheReturnAddress() {
        assertEquals("/manage", SignInDestinations.afterSignIn(MANAGER, "/properties/123"), "a Property Manager ignores the return address");
    }
}
