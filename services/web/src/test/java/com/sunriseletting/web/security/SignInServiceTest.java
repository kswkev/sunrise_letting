package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
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
        service.signIn("account-1", AccountRole.VISITOR, request, response);

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
        service.signIn("account-1", AccountRole.VISITOR, request, response);

        assertInstanceOf(SessionPrincipal.class, SecurityContextHolder.getContext().getAuthentication().getPrincipal(),
                "the rest of this request should see the signed-in principal");
    }

    @Test
    void changesTheSessionIdOfAnExistingSession() {
        MockHttpSession existing = new MockHttpSession();
        String oldId = existing.getId();
        request.setSession(existing);

        service.signIn("account-1", AccountRole.VISITOR, request, response);

        assertNotEquals(oldId, request.getSession(false).getId(), "signing in should change the session id");
    }

    @Test
    void createsASessionWhenThereIsNone() {
        service.signIn("account-1", AccountRole.VISITOR, request, response);

        assertNotNull(request.getSession(false), "signing in should create a session");
    }

    @Test
    void visitorSessionIdlesOutAfterThirtyMinutes() {
        service.signIn("account-1", AccountRole.VISITOR, request, response);

        assertEquals(1800, request.getSession(false).getMaxInactiveInterval(), "a Visitor's session should idle out after 30 minutes");
    }

    @Test
    void tellsListenersAboutTheSignIn() {
        service.signIn("account-1", AccountRole.VISITOR, request, response);

        verify(listener, description("the sign-in hook should hear about the sign-in"))
                .onSignIn(new SessionPrincipal("account-1", AccountRole.VISITOR), request, response);
    }

    @Test
    void listenersAreOptional() {
        SignInService withoutListeners = new SignInService(new HttpSessionSecurityContextRepository(), List.of());

        withoutListeners.signIn("account-1", AccountRole.VISITOR, request, response);

        assertSame(SessionPrincipal.class, SecurityContextHolder.getContext().getAuthentication().getPrincipal().getClass(),
                "signing in should work with no listeners");
    }
}
