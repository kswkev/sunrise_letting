package com.sunriseletting.web.security;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** web's own cookies, with the same attributes as the session cookie: HttpOnly, Secure, SameSite=Lax, path /. */
public class SiteCookies {

    private final boolean secure;

    SiteCookies(boolean secure) {
        this.secure = secure;
    }

    /** The value of a cookie on the request, or null. */
    String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** A cookie that ends with the browser. */
    void add(HttpServletResponse response, String name, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE, builder(name, value).build().toString());
    }

    void add(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, builder(name, value).maxAge(maxAge).build().toString());
    }

    void clear(HttpServletResponse response, String name) {
        add(response, name, "", Duration.ZERO);
    }

    private ResponseCookie.ResponseCookieBuilder builder(String name, String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax").path("/");
    }
}
