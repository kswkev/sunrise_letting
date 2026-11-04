package com.sunriseletting.web.security;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs someone back in from their remember-me cookie, on full-page requests only. A page's images, its HTMX calls and
 * prefetches would race one another for the same token, or spend it on a response nobody reads, and identity treats a
 * token used twice as stolen. Those requests go on as signed out, and the next full page signs the user in.
 */
class RememberMeFilter extends OncePerRequestFilter {

    private final RememberMeLogin login;

    RememberMeFilter(RememberMeLogin login) {
        this.login = login;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !login.hasCookie(request) || !isFullPage(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal)) {
            login.exchange(request, response);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isFullPage(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) || StaticPaths.matches(request) || request.getHeader("HX-Request") != null) {
            return false;
        }
        String destination = request.getHeader("Sec-Fetch-Dest");
        return (destination == null || "document".equals(destination)) && !isPrefetch(request.getHeader("Sec-Purpose"))
                && !isPrefetch(request.getHeader("Purpose"));
    }

    private boolean isPrefetch(String purpose) {
        return purpose != null && purpose.toLowerCase(Locale.ROOT).contains("prefetch");
    }
}
