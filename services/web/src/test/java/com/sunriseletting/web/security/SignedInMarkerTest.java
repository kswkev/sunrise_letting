package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SignedInMarkerTest {

    private final SignedInMarker marker = new SignedInMarker(new SiteCookies(true));

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void signingInSetsAConstantSessionCookieThatIdentifiesNoOne() {
        marker.onSignIn(new SessionPrincipal("account-1", AccountRole.VISITOR, false), request, response);

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.startsWith("signed-in=1;"), "the value is a constant");
        assertFalse(setCookie.contains("account-1"), "no account id in the cookie");
        assertTrue(setCookie.contains("Path=/"), "the whole site should see it");
        assertTrue(setCookie.contains("HttpOnly"), "scripts can't read it");
        assertTrue(setCookie.contains("Secure"), "it only travels over HTTPS");
        assertTrue(setCookie.contains("SameSite=Lax"), "it isn't sent on cross-site POSTs");
        assertFalse(setCookie.contains("Max-Age"), "it ends with the browser");
        assertFalse(setCookie.contains("Expires"), "it ends with the browser");
    }

    @Test
    void signingOutClearsAMarkerThatIsThere() {
        request.setCookies(new Cookie("signed-in", "1"));

        marker.onSignOut(request, response);

        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"), "the marker should be cleared");
    }

    @Test
    void signingOutWithNoMarkerSetsNoCookie() {
        marker.onSignOut(request, response);

        assertNull(response.getHeader("Set-Cookie"), "a cross-site POST carries no cookies, and shouldn't clear any");
    }

    @Test
    void isPresentReadsTheCookie() {
        assertFalse(marker.isPresent(request), "no cookie means no marker");

        request.setCookies(new Cookie("signed-in", "1"));

        assertEquals(true, marker.isPresent(request), "the cookie means the marker");
    }
}
