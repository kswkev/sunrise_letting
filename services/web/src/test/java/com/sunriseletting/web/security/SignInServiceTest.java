package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class SignInServiceTest {

    private static final SessionPrincipal VISITOR = new SessionPrincipal("account-1", AccountRole.VISITOR, false);

    private static final SessionPrincipal TEMPORARY_MANAGER = new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, true);

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final SignInListener listener = mock(SignInListener.class);

    private final SignInService service = new SignInService(new HttpSessionSecurityContextRepository(), List.of(listener));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putsAnAuthenticatedPrincipalWithTheRoleInTheSession() {
        service.signIn(VISITOR, request, response);

        SecurityContext context = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(context, "the security context should be stored in the session");
        Authentication authentication = context.getAuthentication();
        assertTrue(authentication.isAuthenticated(), "the session should be authenticated");
        SessionPrincipal principal = assertInstanceOf(SessionPrincipal.class, authentication.getPrincipal(), "the principal should be ours");
        assertEquals("account-1", principal.accountId(), "the principal should hold the account id");
        assertEquals(AccountRole.VISITOR, principal.role(), "the principal should hold the role");
        assertEquals(List.of("ROLE_VISITOR"), authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                "the only authority should be the role");
    }

    @Test
    void setsTheCurrentSecurityContextForTheRestOfTheRequest() {
        service.signIn(VISITOR, request, response);

        assertInstanceOf(SessionPrincipal.class, SecurityContextHolder.getContext().getAuthentication().getPrincipal(),
                "the rest of this request should see the signed-in principal");
    }

    @Test
    void changesTheSessionIdOfAnExistingSession() {
        MockHttpSession existing = new MockHttpSession();
        String oldId = existing.getId();
        request.setSession(existing);

        service.signIn(VISITOR, request, response);

        assertNotEquals(oldId, request.getSession(false).getId(), "signing in should change the session id");
    }

    @Test
    void createsASessionWhenThereIsNone() {
        service.signIn(VISITOR, request, response);

        assertNotNull(request.getSession(false), "signing in should create a session");
    }

    @Test
    void visitorSessionIdlesOutAfterThirtyMinutes() {
        service.signIn(VISITOR, request, response);

        assertEquals(1800, request.getSession(false).getMaxInactiveInterval(), "a Visitor's session should idle out after 30 minutes");
    }

    @Test
    void propertyManagerSessionIdlesOutAfterTwoHours() {
        service.signIn(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false), request, response);

        assertEquals(7200, request.getSession(false).getMaxInactiveInterval(), "a Property Manager's session should idle out after 2 hours");
    }

    @Test
    void theSessionKeepsTheTemporaryPasswordFlagAndTheManagerAuthority() {
        SessionPrincipal manager = new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, true);

        service.signIn(manager, request, response);

        SecurityContext context = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals(manager, context.getAuthentication().getPrincipal(), "the session should remember that the password is temporary");
        assertEquals(List.of("ROLE_PROPERTY_MANAGER"), context.getAuthentication().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                "the only authority should be the Property Manager role");
    }

    @Test
    void clearsWhatAnEarlierRememberedLoginLeftInTheSession() {
        MockHttpSession existing = new MockHttpSession();
        existing.setAttribute(RememberMeLogin.SESSION_ATTRIBUTE, Boolean.TRUE);
        existing.setAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE, "2030-01-01T00:00:00Z");
        request.setSession(existing);

        service.signIn(VISITOR, request, response);

        assertNull(request.getSession(false).getAttribute(RememberMeLogin.SESSION_ATTRIBUTE),
                "a new sign-in must not keep the earlier login's skipped time-out warning");
        assertNull(request.getSession(false).getAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE),
                "a new sign-in must not keep the earlier login's expiry");
    }

    @Test
    void tellsListenersAboutTheSignIn() {
        service.signIn(VISITOR, request, response);

        verify(listener, description("the sign-in hook should hear about the sign-in"))
                .onSignIn(VISITOR, request, response);
    }

    @Test
    void aPermanentPasswordReplacesTheStoredPrincipalAndKeepsTheAuthority() {
        service.signIn(TEMPORARY_MANAGER, request, response);

        service.passwordIsPermanent(TEMPORARY_MANAGER, request, response);

        SecurityContext context = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false), context.getAuthentication().getPrincipal(),
                "the session should no longer count the password as temporary");
        assertTrue(context.getAuthentication().isAuthenticated(), "the session should stay authenticated");
        assertEquals(List.of("ROLE_PROPERTY_MANAGER"), context.getAuthentication().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                "the only authority should still be the Property Manager role");
        assertEquals(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false), SecurityContextHolder.getContext().getAuthentication().getPrincipal(),
                "the rest of this request should see the new principal");
    }

    @Test
    void aPermanentPasswordIsNotANewSignIn() {
        service.signIn(TEMPORARY_MANAGER, request, response);
        String sessionId = request.getSession(false).getId();
        request.getSession(false).setMaxInactiveInterval(1234);

        service.passwordIsPermanent(TEMPORARY_MANAGER, request, response);

        assertEquals(sessionId, request.getSession(false).getId(), "the session id should stay as it is");
        assertEquals(1234, request.getSession(false).getMaxInactiveInterval(), "the session's idle time-out should stay as it is");
        verify(listener, times(1).description("the sign-in hooks should only have heard about the sign-in"))
                .onSignIn(any(), any(), any());
    }

    @Test
    void listenersAreOptional() {
        SignInService withoutListeners = new SignInService(new HttpSessionSecurityContextRepository(), List.of());

        withoutListeners.signIn(VISITOR, request, response);

        assertSame(SessionPrincipal.class, SecurityContextHolder.getContext().getAuthentication().getPrincipal().getClass(),
                "signing in should work with no listeners");
    }
}
