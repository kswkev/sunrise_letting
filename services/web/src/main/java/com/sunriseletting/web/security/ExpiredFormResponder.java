package com.sunriseletting.web.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers a signed-out form POST whose CSRF token is missing or no longer valid, instead of a 403
 * (design-system.md 3.5 "Expired form"). Each page registers a bean of this type for its own paths.
 * The CSRF filter has already put a usable token on the request, so a re-rendered form gets it.
 * This runs inside the security filter chain, before Spring MVC, so it must not rely on Spring MVC
 * having handled the request. Paths nobody registers still get the 403.
 */
public interface ExpiredFormResponder {

    boolean handles(HttpServletRequest request);

    void respond(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}
