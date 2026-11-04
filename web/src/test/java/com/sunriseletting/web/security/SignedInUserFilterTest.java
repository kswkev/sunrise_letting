package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;

import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.testsupport.Html;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The time-out dialog and script on a real page, whose presence this filter decides. */
@WebMvcTest(PropertiesController.class)
@Import(SecurityConfiguration.class)
class SignedInUserFilterTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void aVisitorSessionThatCanTimeOutHasTheClosedDialogAndScript() {
        Document page = pageFor(visitor(), sessionWithTimeout(1800));

        Element dialog = page.selectFirst("dialog#timeout-dialog.timeout-dialog");
        assertThat(dialog).as("the dialog should be in the page").isNotNull();
        assertThat(dialog.hasAttr("open")).as("the server renders it closed").isFalse();
        assertThat(dialog.attr("role")).as("the role").isEqualTo("alertdialog");
        assertThat(dialog.attr("data-timeout-seconds")).as("the session's own time-out").isEqualTo("1800");
        assertThat(dialog.attr("data-warning-seconds")).as("the warning lead time").isEqualTo("120");
        assertThat(dialog.attr("data-keep-alive-url")).as("the keep-alive address").isEqualTo("/session/keep-alive");
        assertThat(dialog.attr("data-timed-out-url")).as("the timed-out address").isEqualTo("/sign-in?timedOut=true");
        assertThat(page.selectFirst("#" + dialog.attr("aria-labelledby")).text()).as("the heading names it").isEqualTo("You're about to be signed out");
        assertThat(page.selectFirst("#" + dialog.attr("aria-labelledby")).tagName()).as("the heading is an h2").isEqualTo("h2");
        assertThat(page.selectFirst("#" + dialog.attr("aria-describedby")).text()).as("the text describes it")
                .isEqualTo("We will sign you out in 2 minutes unless you choose to stay signed in.");
        Element stay = dialog.selectFirst(".timeout-dialog__actions button[type=button].button.timeout-dialog__stay[autofocus]");
        assertThat(stay.text()).as("the primary button").isEqualTo("Stay signed in");
        Element form = dialog.selectFirst(".timeout-dialog__actions form.sign-out[method=post][action=/sign-out]");
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the sign-out form carries a token").isNotBlank();
        assertThat(form.select("button[type=submit].button.button--secondary").text()).as("the secondary button").isEqualTo("Sign out");
        assertThat(dialog.previousElementSibling().tagName()).as("the dialog comes after the footer").isEqualTo("footer");
        Element script = page.selectFirst("head script[src=/js/session-timeout.js]");
        assertThat(script).as("the script is referenced").isNotNull();
        assertThat(script.hasAttr("defer")).as("the script is deferred").isTrue();
        assertThat(page.select("script:not([src])")).as("no inline script").isEmpty();
    }

    @Test
    void aPropertyManagerSessionGetsItsOwnTimeOut() {
        Authentication manager = UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROPERTY_MANAGER")));

        Document page = pageFor(manager, sessionWithTimeout(7200));

        assertThat(page.select("#timeout-dialog").attr("data-timeout-seconds")).as("the time-out comes from the session").isEqualTo("7200");
    }

    @Test
    void aRememberMeSessionHasNoDialogOrScript() {
        MockHttpSession session = sessionWithTimeout(1800);
        session.setAttribute(RememberMeLogin.SESSION_ATTRIBUTE, true);

        Document page = pageFor(visitor(), session);

        assertThat(page.select("#timeout-dialog")).as("this session doesn't run out, so no warning").isEmpty();
        assertThat(page.select("script[src=/js/session-timeout.js]")).as("and no script").isEmpty();
        assertThat(page.select("header form.sign-out")).as("still signed in").hasSize(1);
    }

    @Test
    void signedOutHasNoDialogOrScript() {
        MvcTestResult result = mvc.get().uri("/properties").exchange();

        Document page = Html.page(result);
        assertThat(page.select("#timeout-dialog")).as("no dialog when signed out").isEmpty();
        assertThat(page.select("script[src=/js/session-timeout.js]")).as("no script when signed out").isEmpty();
    }

    @Test
    void anAnonymousSessionHasNoDialogOrScript() {
        MvcTestResult result = mvc.get().uri("/properties").session(sessionWithTimeout(1800)).exchange();

        assertThat(Html.page(result).select("#timeout-dialog")).as("having a session isn't being signed in").isEmpty();
    }

    @Test
    void theStylesheetStylesTheDialog() {
        MvcTestResult result = mvc.get().uri("/css/site.css").exchange();

        assertThat(result).bodyText().as("the stylesheet should style the dialog").contains(".timeout-dialog").contains("max-width: 30rem").contains(".timeout-dialog__actions");
    }

    private Document pageFor(Authentication user, MockHttpSession session) {
        MvcTestResult result = mvc.get().uri("/properties").session(session).with(authentication(user)).exchange();
        assertThat(result).as("the page should load").hasStatusOk();
        return Html.page(result);
    }

    private MockHttpSession sessionWithTimeout(int seconds) {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(seconds);
        return session;
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
