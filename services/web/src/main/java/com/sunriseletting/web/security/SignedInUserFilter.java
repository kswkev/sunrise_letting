package com.sunriseletting.web.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tells the templates who is signed in, as request attributes, so the shared layout is right on every
 * page, including one rendered from the security filter chain rather than by a controller.
 */
class SignedInUserFilter extends OncePerRequestFilter {

    /** The {@link SessionPrincipal}, absent when no one is signed in. */
    static final String USER_ATTRIBUTE = "signedInUser";

    /** The session's own idle time-out, absent unless the session can run out, which is when the warning applies. */
    static final String TIMEOUT_ATTRIBUTE = "sessionTimeoutSeconds";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return StaticPaths.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        expose(request);
        filterChain.doFilter(request, response);
    }

    static void expose(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            request.setAttribute(USER_ATTRIBUTE, principal);
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(RememberMeLogin.SESSION_ATTRIBUTE) == null && session.getMaxInactiveInterval() > 0) {
                request.setAttribute(TIMEOUT_ATTRIBUTE, session.getMaxInactiveInterval());
            }
        }
    }
}
