package com.sunriseletting.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sign out never shows a 403 page. With no live session the token is stale because the session ended, so
 * this is a normal sign out. A live session isn't ended without its token, because that check is what
 * protects sign out against cross-site requests: the user lands on the search page, still signed in.
 */
class SignOutExpiredFormResponder implements ExpiredFormResponder {

    private final SignOutService signOutService;

    SignOutExpiredFormResponder(SignOutService signOutService) {
        this.signOutService = signOutService;
    }

    @Override
    public boolean handles(HttpServletRequest request) {
        return "/sign-out".equals(request.getRequestURI());
    }

    @Override
    public void respond(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal) {
            SignOutService.redirectToProperties(response);
            return;
        }
        signOutService.signOut(request, response);
    }
}
