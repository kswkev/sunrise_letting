package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

class ExpiredFormAccessDeniedHandlerTest {

    private static final AccessDeniedException CSRF_FAILURE = new MissingCsrfTokenException("stale");

    private final ExpiredFormResponder responder = mock(ExpiredFormResponder.class);

    private final ExpiredFormResponder laterResponder = mock(ExpiredFormResponder.class);

    private final SignInEntryPoint signInEntryPoint = mock(SignInEntryPoint.class);

    private final ViewResolver viewResolver = mock(ViewResolver.class);

    private final View accessDeniedView = mock(View.class);

    private final ExpiredFormAccessDeniedHandler handler = new ExpiredFormAccessDeniedHandler(List.of(responder, laterResponder), signInEntryPoint,
            new SignedInMarker(new SiteCookies(true)), PathPatternRequestMatcher.withDefaults().matcher("/manage/**"), viewResolver);

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // The order: responders, then the marker, then the manager area

    @Test
    void aResponderAnswersBeforeTheMarkerOrTheManagerAreaAreLookedAt() throws Exception {
        MockHttpServletRequest request = post("/manage/x", true);
        when(responder.handles(request)).thenReturn(true);

        handler.handle(request, response, CSRF_FAILURE);

        verify(responder, description("the responder should have been asked")).handles(request);
        verify(responder, description("the responder should answer")).respond(request, response);
        verify(signInEntryPoint, never().description("a responder's form has its own answer, even with the marker on a manager path")).sendToSignIn(any(), any());
        verify(laterResponder, never().description("the first responder that handles the path wins")).handles(any());
    }

    @Test
    void aResponderAlsoAnswersForASignedInUser() throws Exception {
        signInAs(AccountRole.VISITOR);
        MockHttpServletRequest request = post("/manage/x", false);
        when(responder.handles(request)).thenReturn(true);

        handler.handle(request, response, CSRF_FAILURE);

        verify(responder, description("the responder should have been asked")).handles(request);
        verify(responder, description("the responder should answer before the access-denied page")).respond(request, response);
        verify(viewResolver, never().description("no access-denied page")).resolveViewName(any(), any());
    }

    @Test
    void withNoResponderTheMarkerGoesToSignIn() throws Exception {
        MockHttpServletRequest request = post("/properties/1/viewing", true);

        handler.handle(request, response, CSRF_FAILURE);

        verify(signInEntryPoint, description("a session that ran out goes to sign-in")).sendToSignIn(request, response);
        verify(responder, never().description("no responder handled the path")).respond(any(), any());
        assertEquals(200, response.getStatus(), "the handler itself shouldn't have answered a 403");
    }

    @Test
    void withNoResponderOrMarkerTheManagerAreaGoesToSignIn() throws Exception {
        MockHttpServletRequest request = post("/manage/x", false);

        handler.handle(request, response, CSRF_FAILURE);

        verify(signInEntryPoint, description("the manager area needs sign-in anyway")).sendToSignIn(request, response);
        assertEquals(200, response.getStatus(), "the handler itself shouldn't have answered a 403");
    }

    @Test
    void withNoResponderOrMarkerOutsideTheManagerAreaItIsAPlain403() throws Exception {
        MockHttpServletRequest request = post("/properties/1/viewing", false);

        handler.handle(request, response, CSRF_FAILURE);

        assertEquals(403, response.getStatus(), "someone who was never signed in gets a normal 403");
        verify(signInEntryPoint, never().description("no sign-in unless the session ran out or the page needs it")).sendToSignIn(any(), any());
    }

    @Test
    void theMarkerDoesNotSendALiveSessionToSignIn() throws Exception {
        signInAs(AccountRole.VISITOR);
        MockHttpServletRequest request = post("/properties/1/viewing", true);

        handler.handle(request, response, CSRF_FAILURE);

        assertEquals(403, response.getStatus(), "a live session with a bad token is a normal 403");
        verify(signInEntryPoint, never().description("the session hasn't run out")).sendToSignIn(any(), any());
    }

    // The manager area when signed in

    @Test
    void aVisitorInTheManagerAreaWithABadTokenGetsTheAccessDeniedPage() throws Exception {
        signInAs(AccountRole.VISITOR);
        MockHttpServletRequest request = post("/manage/x", true);
        when(viewResolver.resolveViewName("access-denied", Locale.ENGLISH)).thenReturn(accessDeniedView);
        request.addPreferredLocale(Locale.ENGLISH);

        handler.handle(request, response, CSRF_FAILURE);

        assertEquals(403, response.getStatus(), "the page should say Forbidden");
        verify(viewResolver, description("the access-denied page should be looked up")).resolveViewName("access-denied", Locale.ENGLISH);
        verify(accessDeniedView, description("the access-denied page should be rendered")).render(Map.of(), request, response);
        verify(signInEntryPoint, never().description("a Visitor is already signed in")).sendToSignIn(any(), any());
    }

    @Test
    void aVisitorDeniedTheManagerAreaWithNoCsrfProblemGetsTheAccessDeniedPageWithoutAskingResponders() throws Exception {
        signInAs(AccountRole.VISITOR);
        MockHttpServletRequest request = post("/manage/x", false);
        when(viewResolver.resolveViewName("access-denied", Locale.ENGLISH)).thenReturn(accessDeniedView);
        request.addPreferredLocale(Locale.ENGLISH);

        handler.handle(request, response, new AccessDeniedException("not a manager"));

        assertEquals(403, response.getStatus(), "the page should say Forbidden");
        verify(viewResolver, description("the access-denied page should be looked up")).resolveViewName("access-denied", Locale.ENGLISH);
        verify(accessDeniedView, description("the access-denied page should be rendered")).render(Map.of(), request, response);
        verify(responder, never().description("responders are for expired forms only")).handles(any());
    }

    @Test
    void aPropertyManagerWithABadTokenGetsAPlain403() throws Exception {
        signInAs(AccountRole.PROPERTY_MANAGER);
        MockHttpServletRequest request = post("/manage/x", false);

        handler.handle(request, response, CSRF_FAILURE);

        assertEquals(403, response.getStatus(), "a Property Manager may be here, so it's only the token that is wrong");
        verify(viewResolver, never().description("the access-denied page is for Visitors")).resolveViewName(any(), any());
        verify(signInEntryPoint, never().description("the Property Manager is signed in")).sendToSignIn(any(), any());
    }

    private MockHttpServletRequest post(String path, boolean withMarker) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (withMarker) {
            request.setCookies(new Cookie(SignedInMarker.COOKIE, "1"));
        }
        return request;
    }

    private void signInAs(AccountRole role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", role), null,
                List.of(new SimpleGrantedAuthority(role.authority()))));
    }
}
