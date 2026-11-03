package com.sunriseletting.web.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides what a CSRF failure means. Registered paths go to their {@link ExpiredFormResponder}, first, because
 * those forms have their own answer even when the signed-in marker is present. Otherwise someone with no live
 * session goes to sign-in if their session ran out or the page needs sign-in anyway. Everything else is a normal 403.
 */
class ExpiredFormAccessDeniedHandler implements AccessDeniedHandler {

    private final List<ExpiredFormResponder> responders;

    private final SignInEntryPoint signInEntryPoint;

    private final SignedInMarker marker;

    private final RequestMatcher pagesThatNeedSignIn;

    private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();

    ExpiredFormAccessDeniedHandler(List<ExpiredFormResponder> responders, SignInEntryPoint signInEntryPoint, SignedInMarker marker,
            RequestMatcher pagesThatNeedSignIn) {
        this.responders = responders;
        this.signInEntryPoint = signInEntryPoint;
        this.marker = marker;
        this.pagesThatNeedSignIn = pagesThatNeedSignIn;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        if (exception instanceof CsrfException) {
            for (ExpiredFormResponder responder : responders) {
                if (responder.handles(request)) {
                    responder.respond(request, response);
                    return;
                }
            }
            if (!isSignedIn() && (marker.isPresent(request) || pagesThatNeedSignIn.matches(request))) {
                signInEntryPoint.sendToSignIn(request, response);
                return;
            }
        }
        otherwise.handle(request, response, exception);
    }

    private boolean isSignedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SessionPrincipal;
    }
}
