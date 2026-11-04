package com.sunriseletting.web.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Until a Property Manager replaces a temporary password, every Property Manager page except the
 * change-password page itself sends them there. It runs after the access rules, so only Property Managers reach it.
 */
class TemporaryPasswordFilter extends OncePerRequestFilter {

    private final RequestMatcher managerArea;

    private final RequestMatcher changePassword = PathPatternRequestMatcher.withDefaults().matcher(SignInDestinations.CHANGE_PASSWORD);

    TemporaryPasswordFilter(RequestMatcher managerArea) {
        this.managerArea = managerArea;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !managerArea.matches(request) || changePassword.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal && principal.temporaryPassword()) {
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader(HttpHeaders.LOCATION, SignInDestinations.CHANGE_PASSWORD);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
