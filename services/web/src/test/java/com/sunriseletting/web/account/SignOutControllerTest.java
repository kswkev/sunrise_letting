package com.sunriseletting.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.security.SignOutListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(SignOutController.class)
@Import(SecurityConfiguration.class)
class SignOutControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SignOutListener listener;

    @Test
    void signingOutInvalidatesTheSessionAndGoesToProperties() {
        MockHttpSession session = new MockHttpSession();

        MvcTestResult result = mvc.post().uri("/sign-out").session(session).with(authentication(visitor())).with(csrf()).exchange();

        assertThat(result).as("sign out should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        assertThat(session.isInvalid()).as("the session should end").isTrue();
        verify(listener, description("the hook runs on a normal sign out")).onSignOut(any(), any());
    }

    @Test
    void signingOutWithNoLiveSessionAndAValidTokenGoesToProperties() {
        MvcTestResult result = mvc.post().uri("/sign-out").with(csrf()).exchange();

        assertThat(result).as("sign out should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        verify(listener, description("the hook runs when the session has already ended")).onSignOut(any(), any());
    }

    @Test
    void aMissingTokenWithNoLiveSessionStillSignsOutWithNo403() {
        MvcTestResult result = mvc.post().uri("/sign-out").exchange();

        assertThat(result).as("a missing token shouldn't give a 403 page").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        verify(listener, description("the hook runs in the stale-token case")).onSignOut(any(), any());
    }

    @Test
    void aStaleTokenWithNoLiveSessionStillSignsOutWithNo403() {
        MvcTestResult result = mvc.post().uri("/sign-out").with(csrf().useInvalidToken()).exchange();

        assertThat(result).as("a stale token shouldn't give a 403 page").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        verify(listener, description("the hook runs in the stale-token case")).onSignOut(any(), any());
    }

    /** A POST without the token must not end a live session, or sign out would no longer be protected against cross-site requests. */
    @Test
    void aBadTokenOnALiveSessionDoesNotEndItAndStillShowsNo403() {
        MockHttpSession session = new MockHttpSession();

        MvcTestResult result = mvc.post().uri("/sign-out").session(session).with(authentication(visitor())).exchange();

        assertThat(result).as("there is no 403 page on sign out").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("the user lands on the search page").hasRedirectedUrl("/properties");
        assertThat(session.isInvalid()).as("an unprotected POST shouldn't end the session").isFalse();
        verify(listener, never().description("nothing is revoked or cleared")).onSignOut(any(), any());
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
