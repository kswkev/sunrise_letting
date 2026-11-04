package com.sunriseletting.web.security;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/** Starts a signed-in session once identity has confirmed an account. */
public class SignInService {

    private static final Duration VISITOR_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private static final Duration PROPERTY_MANAGER_IDLE_TIMEOUT = Duration.ofHours(2);

    private final SecurityContextRepository securityContextRepository;

    private final List<SignInListener> listeners;

    SignInService(SecurityContextRepository securityContextRepository, List<SignInListener> listeners) {
        this.securityContextRepository = securityContextRepository;
        this.listeners = listeners;
    }

    /**
     * Puts the principal in the session under a new session id (session fixation protection), and
     * sets the session's own idle time-out, which Redis keeps with it for whichever replica is next.
     * An earlier remembered login's long life and skipped warning end here; a login remembered by this sign-in sets them again afterwards.
     */
    public void signIn(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        request.getSession(false).setMaxInactiveInterval((int) idleTimeout(principal.role()).toSeconds());
        request.getSession(false).removeAttribute(RememberMeLogin.SESSION_ATTRIBUTE);
        request.getSession(false).removeAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE);

        storeInSession(principal, request, response);

        listeners.forEach(listener -> listener.onSignIn(principal, request, response));
    }

    /**
     * Records in the session that a Property Manager no longer has a temporary password. The principal is immutable, so it is
     * replaced. This isn't a new sign-in: the session id and idle time-out stay as they are, and the listeners don't run.
     */
    public SessionPrincipal passwordIsPermanent(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        SessionPrincipal permanent = new SessionPrincipal(principal.accountId(), principal.role(), false);
        storeInSession(permanent, request, response);
        return permanent;
    }

    private void storeInSession(SessionPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(principal.role().authority()))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static Duration idleTimeout(AccountRole role) {
        return switch (role) {
            case VISITOR -> VISITOR_IDLE_TIMEOUT;
            case PROPERTY_MANAGER -> PROPERTY_MANAGER_IDLE_TIMEOUT;
        };
    }
}
