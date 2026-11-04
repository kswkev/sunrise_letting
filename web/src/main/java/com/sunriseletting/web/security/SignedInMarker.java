package com.sunriseletting.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tells a session that ran out from someone who was never signed in. A constant cookie that identifies no one,
 * set at sign-in and cleared at sign-out, and also as soon as the user is told about a time-out, so that message shows once.
 */
public class SignedInMarker implements SignInListener, SignOutListener {

    static final String COOKIE = "signed-in";

    private static final String VALUE = "1";

    private final SiteCookies cookies;

    SignedInMarker(SiteCookies cookies) {
        this.cookies = cookies;
    }

    public boolean isPresent(HttpServletRequest request) {
        return cookies.read(request, COOKIE) != null;
    }

    /** Clears the marker if the request carries it. */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        if (isPresent(request)) {
            cookies.clear(response, COOKIE);
        }
    }

    @Override
    public void onSignIn(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        cookies.add(response, COOKIE, VALUE);
    }

    @Override
    public void onSignOut(HttpServletRequest request, HttpServletResponse response) {
        clear(request, response);
    }
}
