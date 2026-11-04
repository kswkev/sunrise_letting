package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SignInEntryPointTest {

    private final SignInEntryPoint entryPoint = new SignInEntryPoint(new SignedInMarker(new SiteCookies(true)));

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void expiredGetGoesToTheTimedOutAddressWithThePathAndQueryAndClearsTheMarker() throws IOException {
        MockHttpServletRequest request = request("GET", "/x");
        request.setQueryString("a=1");
        withMarker(request);

        entryPoint.commence(request, response, null);

        assertEquals(302, response.getStatus(), "the browser should be redirected");
        assertEquals("/sign-in?timedOut=true&returnTo=%2Fx%3Fa%3D1", response.getHeader("Location"), "the return address is encoded");
        assertMarkerCleared();
    }

    @Test
    void expiredPostReturnsToTheRequestPathOnly() throws IOException {
        MockHttpServletRequest request = request("POST", "/properties/123/viewing");
        request.setQueryString("a=1");
        withMarker(request);

        entryPoint.commence(request, response, null);

        assertEquals("/sign-in?timedOut=true&returnTo=%2Fproperties%2F123%2Fviewing", response.getHeader("Location"), "a form posts to its own page");
        assertMarkerCleared();
    }

    @Test
    void expiredHtmxRequestGetsAnHxRedirectForThePageItCameFrom() throws IOException {
        MockHttpServletRequest request = request("GET", "/properties/search");
        request.addHeader("HX-Request", "true");
        request.addHeader("HX-Current-URL", "http://localhost/properties?q=leeds");
        withMarker(request);

        entryPoint.commence(request, response, null);

        assertEquals("/sign-in?timedOut=true&returnTo=%2Fproperties%3Fq%3Dleeds", response.getHeader("HX-Redirect"), "the return address is the page, not the endpoint");
        assertNull(response.getHeader("Location"), "a real redirect would swap the sign-in page into a region");
        assertEquals(200, response.getStatus(), "HTMX acts on the header of a normal response");
        assertMarkerCleared();
    }

    @Test
    void htmxRequestWithAnUnreadableCurrentUrlFallsBackToItsOwnAddress() throws IOException {
        MockHttpServletRequest request = request("GET", "/properties/search");
        request.addHeader("HX-Request", "true");
        request.addHeader("HX-Current-URL", "::not a url::");
        withMarker(request);

        entryPoint.commence(request, response, null);

        assertEquals("/sign-in?timedOut=true&returnTo=%2Fproperties%2Fsearch", response.getHeader("HX-Redirect"), "there's still somewhere to return to");
    }

    @Test
    void neverSignedInGoesToSignInWithNoInactivityMessage() throws IOException {
        MockHttpServletRequest request = request("GET", "/x");
        request.setQueryString("a=1");

        entryPoint.commence(request, response, null);

        assertEquals(302, response.getStatus(), "the browser should be redirected");
        assertEquals("/sign-in?returnTo=%2Fx%3Fa%3D1", response.getHeader("Location"), "no timedOut for someone who was never signed in");
        assertNull(response.getHeader("Set-Cookie"), "there's no marker to clear");
    }

    @Test
    void neverSignedInHtmxRequestGetsAnHxRedirectToo() throws IOException {
        MockHttpServletRequest request = request("GET", "/x");
        request.addHeader("HX-Request", "true");
        request.addHeader("HX-Current-URL", "http://localhost/properties");

        entryPoint.commence(request, response, null);

        assertEquals("/sign-in?returnTo=%2Fproperties", response.getHeader("HX-Redirect"), "the whole page should change, with no message");
    }

    @Test
    void aPathThatFailsTheReturnAddressRulesIsLeftOut() throws IOException {
        MockHttpServletRequest request = request("GET", "/x%5cy");
        withMarker(request);

        entryPoint.commence(request, response, null);

        assertEquals("/sign-in?timedOut=true", response.getHeader("Location"), "an unsafe path isn't passed on");
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private void withMarker(MockHttpServletRequest request) {
        request.setCookies(new Cookie(SignedInMarker.COOKIE, "1"));
    }

    private void assertMarkerCleared() {
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.startsWith("signed-in=;"), "the marker should be cleared, so the message shows once");
        assertTrue(setCookie.contains("Max-Age=0"), "the marker should be cleared at once");
        assertTrue(setCookie.contains("Path=/") && setCookie.contains("HttpOnly") && setCookie.contains("Secure") && setCookie.contains("SameSite=Lax"),
                "the clearing cookie has the same attributes");
        assertFalse(setCookie.contains("account"), "the marker identifies no one");
    }
}
