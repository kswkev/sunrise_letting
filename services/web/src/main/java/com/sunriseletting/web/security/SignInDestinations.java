package com.sunriseletting.web.security;

/** Where people go after signing in, and when they open a sign-in or sign-up page already signed in (design-system.md 3.5). */
public final class SignInDestinations {

    private static final String PROPERTIES = "/properties";

    private static final String MANAGE = "/manage";

    private static final String CHANGE_PASSWORD = "/manage/change-password";

    private SignInDestinations() {
    }

    /** After a Visitor signs in or signs up: the return address if it is valid, otherwise the search page. */
    public static String forVisitor(String returnTo) {
        String valid = ReturnAddress.validOrNull(returnTo);
        return valid != null ? valid : PROPERTIES;
    }

    /** After someone signs in, and for a tab whose timer sent it to sign in while its session is still active. Property Managers ignore the return address. */
    public static String afterSignIn(SessionPrincipal principal, String returnTo) {
        return principal.role() == AccountRole.PROPERTY_MANAGER ? forPropertyManager(principal) : forVisitor(returnTo);
    }

    /** When someone already signed in opens a sign-in or sign-up page. Any return address is ignored. */
    public static String whenAlreadySignedIn(SessionPrincipal principal) {
        return principal.role() == AccountRole.PROPERTY_MANAGER ? forPropertyManager(principal) : PROPERTIES;
    }

    private static String forPropertyManager(SessionPrincipal principal) {
        return principal.temporaryPassword() ? CHANGE_PASSWORD : MANAGE;
    }
}
