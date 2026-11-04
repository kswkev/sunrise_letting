package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class RememberMeSessionFilterTest {

    private final RememberMeSessionFilter filter = new RememberMeSessionFilter();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/properties");

    private final MockFilterChain chain = new MockFilterChain();

    @Test
    void eachRequestShortensTheIntervalToWhatIsLeftOfTheLogin() throws Exception {
        MockHttpSession session = rememberedSession(Instant.now().plus(Duration.ofDays(10)));
        session.setMaxInactiveInterval((int) Duration.ofDays(30).toSeconds());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat((long) session.getMaxInactiveInterval()).as("activity mustn't carry the session past the end of the login")
                .isCloseTo(Duration.ofDays(10).toSeconds(), within(60L));
        assertThat(chain.getRequest()).as("the request should carry on").isSameAs(request);
    }

    @Test
    void aLoginAtItsEndLeavesTheShortestIntervalNotOneThatNeverEnds() throws Exception {
        MockHttpSession session = rememberedSession(Instant.now().minus(Duration.ofMinutes(1)));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.getMaxInactiveInterval()).as("zero or less would mean a session that never runs out").isEqualTo(1);
    }

    @Test
    void anOrdinarySessionKeepsItsInterval() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(1800);
        request.setSession(session);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.getMaxInactiveInterval()).as("only remember-me sessions are capped").isEqualTo(1800);
    }

    @Test
    void noSessionIsCreated() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getSession(false)).as("a signed-out request has no session to change").isNull();
        assertThat(chain.getRequest()).as("the request should carry on").isSameAs(request);
    }

    private MockHttpSession rememberedSession(Instant expiresAt) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RememberMeLogin.EXPIRES_AT_ATTRIBUTE, expiresAt.toString());
        request.setSession(session);
        return session;
    }
}