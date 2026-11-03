package com.sunriseletting.web.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/** Ends a session. Invalidating it deletes it from Redis, and Spring Session then clears its cookie. */
public class SignOutService {

    private static final String AFTER_SIGN_OUT = "/properties";

    private final SecurityContextRepository securityContextRepository;

    private final List<SignOutListener> listeners;

    SignOutService(SecurityContextRepository securityContextRepository, List<SignOutListener> listeners) {
        this.securityContextRepository = securityContextRepository;
        this.listeners = listeners;
    }

    /** Runs the sign-out hooks, ends the session if there is one, and sends the browser to the search page. */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        listeners.forEach(listener -> listener.onSignOut(request, response));

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        securityContextRepository.saveContext(empty, request, response);

        redirectToProperties(response);
    }

    static void redirectToProperties(HttpServletResponse response) {
        response.setStatus(HttpStatus.SEE_OTHER.value());
        response.setHeader(HttpHeaders.LOCATION, AFTER_SIGN_OUT);
    }
}
