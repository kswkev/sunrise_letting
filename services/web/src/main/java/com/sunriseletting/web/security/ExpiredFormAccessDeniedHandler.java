package com.sunriseletting.web.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.ViewResolver;

/**
 * Decides what a denied request means. On a CSRF failure, registered paths go to their {@link ExpiredFormResponder}, first,
 * because those forms have their own answer even when the signed-in marker is present. Otherwise someone with no live
 * session goes to sign-in if their session ran out or the page needs sign-in anyway. A Visitor in the Property Manager
 * area gets the full access-denied page. Everything else is a normal 403.
 */
class ExpiredFormAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ACCESS_DENIED_VIEW = "access-denied";

    private final List<ExpiredFormResponder> responders;

    private final SignInEntryPoint signInEntryPoint;

    private final SignedInMarker marker;

    private final RequestMatcher managerArea;

    private final ViewResolver viewResolver;

    private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();

    ExpiredFormAccessDeniedHandler(List<ExpiredFormResponder> responders, SignInEntryPoint signInEntryPoint, SignedInMarker marker,
            RequestMatcher managerArea, ViewResolver viewResolver) {
        this.responders = responders;
        this.signInEntryPoint = signInEntryPoint;
        this.marker = marker;
        this.managerArea = managerArea;
        this.viewResolver = viewResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        SessionPrincipal principal = signedInUser();
        if (exception instanceof CsrfException) {
            for (ExpiredFormResponder responder : responders) {
                if (responder.handles(request)) {
                    responder.respond(request, response);
                    return;
                }
            }
            if (principal == null && (marker.isPresent(request) || managerArea.matches(request))) {
                signInEntryPoint.sendToSignIn(request, response);
                return;
            }
        }
        if (principal != null && principal.role() == AccountRole.VISITOR && managerArea.matches(request)) {
            showAccessDenied(request, response);
            return;
        }
        otherwise.handle(request, response, exception);
    }

    private void showAccessDenied(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        SignedInUserFilter.expose(request);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        try {
            viewResolver.resolveViewName(ACCESS_DENIED_VIEW, request.getLocale()).render(Map.of(), request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private SessionPrincipal signedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal ? principal : null;
    }
}
