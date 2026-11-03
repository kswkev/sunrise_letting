package com.sunriseletting.web.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tells the templates who is signed in, as a request attribute, so the shared header is right on every
 * page, including one rendered from the security filter chain rather than by a controller.
 */
class SignedInUserFilter extends OncePerRequestFilter {

    /** The {@link SessionPrincipal}, absent when no one is signed in. */
    static final String ATTRIBUTE = "signedInUser";

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
            request.setAttribute(ATTRIBUTE, principal);
        }
    }
}
