package com.sunriseletting.web.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/** Stylesheets, scripts and the like. These never need to know who is signed in, so they don't read the session. */
final class StaticPaths {

    private static final List<String> PREFIXES = List.of("/css/", "/js/", "/webjars/", "/favicon.ico");

    private StaticPaths() {
    }

    static boolean matches(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
