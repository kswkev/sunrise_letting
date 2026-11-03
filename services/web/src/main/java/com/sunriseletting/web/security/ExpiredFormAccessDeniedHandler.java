package com.sunriseletting.web.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

/** Hands CSRF failures on registered form paths to their {@link ExpiredFormResponder}. Everything else is a normal 403. */
class ExpiredFormAccessDeniedHandler implements AccessDeniedHandler {

    private final List<ExpiredFormResponder> responders;

    private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();

    ExpiredFormAccessDeniedHandler(List<ExpiredFormResponder> responders) {
        this.responders = responders;
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
        }
        otherwise.handle(request, response, exception);
    }
}
