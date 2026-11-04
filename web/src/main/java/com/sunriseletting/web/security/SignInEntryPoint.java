package com.sunriseletting.web.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UriUtils;

/**
 * Sends someone with no signed-in session to the sign-in page. The marker tells a session that ran out, which
 * gets the inactivity message, from someone who was never signed in. An HTMX request gets {@code HX-Redirect},
 * because a real redirect would swap the sign-in page into a region of the page.
 */
class SignInEntryPoint implements AuthenticationEntryPoint {

    private static final String SIGN_IN = "/sign-in";

    private final SignedInMarker marker;

    SignInEntryPoint(SignedInMarker marker) {
        this.marker = marker;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        sendToSignIn(request, response);
    }

    void sendToSignIn(HttpServletRequest request, HttpServletResponse response) {
        String address = signInAddress(request);
        marker.clear(request, response);
        if (request.getHeader("HX-Request") != null) {
            response.setHeader("HX-Redirect", address);
        } else {
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader(HttpHeaders.LOCATION, address);
        }
    }

    private String signInAddress(HttpServletRequest request) {
        String returnTo = ReturnAddress.validOrNull(returnAddress(request));
        String encodedReturnTo = returnTo == null ? null : "returnTo=" + UriUtils.encode(returnTo, StandardCharsets.UTF_8);
        if (marker.isPresent(request)) {
            return SIGN_IN + "?timedOut=true" + (encodedReturnTo == null ? "" : "&" + encodedReturnTo);
        }
        return SIGN_IN + (encodedReturnTo == null ? "" : "?" + encodedReturnTo);
    }

    /** A POST returns to its path: a form posts to the page it is on, which then opens with an empty form. */
    private String returnAddress(HttpServletRequest request) {
        String page = htmxCurrentPage(request);
        if (page != null) {
            return page;
        }
        String query = request.getQueryString();
        return HttpMethod.GET.matches(request.getMethod()) && query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();
    }

    /** The path and query of the page an HTMX request came from, or null if there is no such header or it can't be read. */
    private String htmxCurrentPage(HttpServletRequest request) {
        String currentUrl = request.getHeader("HX-Current-URL");
        if (request.getHeader("HX-Request") == null || currentUrl == null) {
            return null;
        }
        try {
            URI uri = URI.create(currentUrl);
            if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
                return null;
            }
            return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
