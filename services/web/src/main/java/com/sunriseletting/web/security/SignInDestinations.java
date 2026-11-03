package com.sunriseletting.web.security;

/** Where people go after signing in, and when they open a sign-in or sign-up page already signed in (design-system.md 3.5). */
public final class SignInDestinations {

    private static final String PROPERTIES = "/properties";

    private SignInDestinations() {
    }

    /** After a Visitor signs in or signs up: the return address if it is valid, otherwise the search page. */
    public static String forVisitor(String returnTo) {
        String valid = ReturnAddress.validOrNull(returnTo);
        return valid != null ? valid : PROPERTIES;
    }

    /** When someone already signed in opens a sign-in or sign-up page. Any return address is ignored. */
    public static String whenAlreadySignedIn(SessionPrincipal principal) {
        return PROPERTIES;
    }
}
