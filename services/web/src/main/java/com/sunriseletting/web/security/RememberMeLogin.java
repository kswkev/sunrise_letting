package com.sunriseletting.web.security;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.identity.RememberMeTokenResult;
import com.sunriseletting.web.identity.RememberMeVerifyResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * The browser's side of a remember-me login. Identity stores the tokens and checks them, including the rule
 * that a replaced token revokes everything. web only holds them in a cookie, as {@code series.token}.
 */
@Component
public class RememberMeLogin implements SignOutListener {

    /** Set on a session that a remember-me login keeps going, which therefore never shows the time-out warning. */
    public static final String SESSION_ATTRIBUTE = "rememberMeSession";

    /** When the login behind a remembered session ends, as an ISO-8601 instant. */
    static final String EXPIRES_AT_ATTRIBUTE = "rememberMeExpiresAt";

    static final String COOKIE = "remember-me";

    /** Unpadded URL-safe Base64: a series of 22 characters and a token of 43. */
    private static final Pattern COOKIE_VALUE = Pattern.compile("([A-Za-z0-9_-]{22})\\.([A-Za-z0-9_-]{43})");

    private final IdentityClient identityClient;

    private final SignInService signInService;

    private final SiteCookies cookies;

    private final RememberMeExchangeGate gate;

    RememberMeLogin(IdentityClient identityClient, SignInService signInService, SiteCookies cookies, RememberMeExchangeGate gate) {
        this.identityClient = identityClient;
        this.signInService = signInService;
        this.cookies = cookies;
        this.gate = gate;
    }

    /** After someone signed in with the box ticked. If no token can be had, they simply have an ordinary session. */
    public void remember(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (principal.role() != AccountRole.VISITOR) {
            return;
        }
        if (identityClient.issueRememberMeToken(principal.accountId()) instanceof RememberMeTokenResult.Issued issued) {
            writeCookie(response, issued.series(), issued.token(), issued.expiresAt());
            keepSession(request, issued.expiresAt());
        }
    }

    boolean hasCookie(HttpServletRequest request) {
        return cookies.read(request, COOKIE) != null;
    }

    /** On a request with no signed-in session: swaps the cookie for a signed-in session, if it is good and no other request is doing so. */
    void exchange(HttpServletRequest request, HttpServletResponse response) {
        Matcher value = parse(request);
        if (value == null) {
            cookies.clear(response, COOKIE);
            return;
        }
        String series = value.group(1);
        if (!gate.tryEnter(series)) {
            return;
        }
        RememberMeVerifyResult result = identityClient.verifyRememberMeToken(series, value.group(2));
        if (result instanceof RememberMeVerifyResult.Verified verified) {
            signInService.signIn(verified.accountId(), verified.role(), request, response);
            keepSession(request, verified.expiresAt());
            writeCookie(response, series, verified.token(), verified.expiresAt());
            return;
        }
        gate.reopen(series);
        if (result instanceof RememberMeVerifyResult.Invalid) {
            cookies.clear(response, COOKIE);
        }
    }

    @Override
    public void onSignOut(HttpServletRequest request, HttpServletResponse response) {
        if (!hasCookie(request)) {
            return;
        }
        Matcher value = parse(request);
        if (value != null) {
            identityClient.revokeRememberMeToken(value.group(1));
        }
        cookies.clear(response, COOKIE);
    }

    private Matcher parse(HttpServletRequest request) {
        Matcher matcher = COOKIE_VALUE.matcher(cookies.read(request, COOKIE));
        return matcher.matches() ? matcher : null;
    }

    /** Using a token doesn't extend its life, so the cookie lasts as long as identity says, never a fresh 30 days. */
    private void writeCookie(HttpServletResponse response, String series, String token, Instant expiresAt) {
        cookies.add(response, COOKIE, series + "." + token, Duration.between(Instant.now(), expiresAt));
    }

    /** The session lives as long as the login, so a form left open isn't lost to an idle time-out, and is flagged to skip the warning. */
    private void keepSession(HttpServletRequest request, Instant expiresAt) {
        HttpSession session = request.getSession();
        session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, expiresAt.toString());
        capToLogin(session);
    }

    /**
     * Sets a remembered session's idle time-out to what is left of its login. Each request restarts the idle timer, so this
     * has to be repeated on each one, or activity would carry the session past the login's fixed expiry. Never below a second,
     * because an interval of zero or less can mean a session that never runs out.
     */
    static void capToLogin(HttpSession session) {
        if (session.getAttribute(EXPIRES_AT_ATTRIBUTE) instanceof String expiresAt) {
            long remaining = Duration.between(Instant.now(), Instant.parse(expiresAt)).toSeconds();
            session.setMaxInactiveInterval((int) Math.max(1, Math.min(remaining, Integer.MAX_VALUE)));
        }
    }
}
