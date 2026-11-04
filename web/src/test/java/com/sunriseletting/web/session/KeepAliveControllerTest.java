package com.sunriseletting.web.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.testsupport.Html;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(KeepAliveController.class)
@Import(SecurityConfiguration.class)
class KeepAliveControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void aSignedInSessionGets204() {
        MvcTestResult result = mvc.post().uri("/session/keep-alive").with(authentication(visitor())).with(csrf()).exchange();

        assertThat(result).as("the request itself resets the idle timer").hasStatus(HttpStatus.NO_CONTENT);
        assertThat(Html.body(result)).as("no content").isEmpty();
    }

    @Test
    void noSessionWithAGoodTokenGets401() {
        assertBareUnauthorized(mvc.post().uri("/session/keep-alive").with(csrf()).exchange());
    }

    @Test
    void anExpiredSessionWithAStaleTokenGets401EvenWithTheMarker() {
        MvcTestResult result = mvc.post().uri("/session/keep-alive").cookie(new Cookie("signed-in", "1")).with(csrf().useInvalidToken()).exchange();

        assertBareUnauthorized(result);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).as("the marker goes when the script reaches the timed-out address")
                .noneMatch(header -> header.startsWith("signed-in="));
    }

    @Test
    void noTokenAtAllGets401() {
        assertBareUnauthorized(mvc.post().uri("/session/keep-alive").exchange());
    }

    @Test
    void anHtmxStyleRequestStillGetsNoRedirect() {
        assertBareUnauthorized(mvc.post().uri("/session/keep-alive").header("HX-Request", "true").cookie(new Cookie("signed-in", "1")).exchange());
    }

    private void assertBareUnauthorized(MvcTestResult result) {
        assertThat(result).as("the script can tell the session ended").hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(Html.body(result)).as("there is no body").isEmpty();
        assertThat(result.getResponse().getHeader("Location")).as("there is no redirect").isNull();
        assertThat(result.getResponse().getHeader("HX-Redirect")).as("there is no HX-Redirect").isNull();
    }

    private UsernamePasswordAuthenticationToken visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
