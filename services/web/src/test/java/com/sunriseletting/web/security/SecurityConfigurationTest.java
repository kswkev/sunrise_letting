package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import com.sunriseletting.web.home.HomeController;
import com.sunriseletting.web.testsupport.Html;
import jakarta.servlet.http.Cookie;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(HomeController.class)
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void pagesArePublic() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("a page should load with no one signed in").hasStatusOk();
    }

    @Test
    void postWithoutACsrfTokenIsRejected() {
        MvcTestResult result = mvc.post().uri("/").exchange();

        assertThat(result).as("a POST with no CSRF token should be rejected").hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWithAnInvalidCsrfTokenIsRejected() {
        MvcTestResult result = mvc.post().uri("/").with(csrf().useInvalidToken()).exchange();

        assertThat(result).as("a POST with a wrong CSRF token should be rejected").hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWithACsrfTokenGetsPastTheCheck() {
        MvcTestResult result = mvc.post().uri("/").with(csrf()).exchange();

        assertThat(result).as("the home page has no POST, so a good token should reach Spring MVC")
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void expiredPostWithoutACsrfTokenGoesToTheTimedOutAddressNotA403() {
        MvcTestResult result = mvc.post().uri("/some/form").cookie(new Cookie("signed-in", "1")).exchange();

        assertThat(result).as("an expired session should redirect, not 403").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("the return address is the request path").hasRedirectedUrl("/sign-in?timedOut=true&returnTo=%2Fsome%2Fform");
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("the marker should be cleared").startsWith("signed-in=;").contains("Max-Age=0");
    }

    @Test
    void expiredHtmxPostWithoutACsrfTokenGetsAnHxRedirect() {
        MvcTestResult result = mvc.post().uri("/some/fragment").header("HX-Request", "true").header("HX-Current-URL", "http://localhost/properties?q=leeds")
                .cookie(new Cookie("signed-in", "1")).exchange();

        assertThat(result).as("HTMX acts on the header of a normal response").hasStatusOk();
        assertThat(result.getResponse().getHeader("HX-Redirect")).as("the return address is the page")
                .isEqualTo("/sign-in?timedOut=true&returnTo=%2Fproperties%3Fq%3Dleeds");
    }

    @Test
    void aBadCsrfTokenOnALiveSessionIsStillRejected() {
        MvcTestResult result = mvc.post().uri("/some/form").cookie(new Cookie("signed-in", "1")).with(authentication(visitor())).exchange();

        assertThat(result).as("the session hasn't expired, so this is a real CSRF failure").hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("the marker stays").isNull();
    }

    @Test
    void neverSignedInPostToAPageThatNeedsSignInGoesToSignInWithNoMessage() {
        MvcTestResult result = mvc.post().uri("/manage/anything").exchange();

        assertThat(result).as("this page needs sign-in, so it isn't a 403").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("no timedOut for someone never signed in").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage%2Fanything");
    }

    @Test
    void expiredGetOfAPageThatNeedsSignInGoesToTheTimedOutAddress() {
        MvcTestResult result = mvc.get().uri("/manage/x?a=1").cookie(new Cookie("signed-in", "1")).exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("the return address keeps the query").hasRedirectedUrl("/sign-in?timedOut=true&returnTo=%2Fmanage%2Fx%3Fa%3D1");
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("the marker should be cleared").contains("Max-Age=0");
    }

    @Test
    void neverSignedInGetOfAPageThatNeedsSignInGoesToSignInWithNoMessage() {
        MvcTestResult result = mvc.get().uri("/manage").exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("no timedOut for someone never signed in").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/manage", "/manage/", "/manage/anything", "/manage/change-password/x" })
    void aVisitorGetsTheAccessDeniedPageAndKeepsTheirSession(String path) {
        MockHttpSession session = new MockHttpSession();

        MvcTestResult result = mvc.get().uri(path).session(session).with(authentication(visitor())).exchange();

        assertThat(result).as("a Visitor is denied, even where no page exists yet").hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).as("the page is HTML").hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
        Document page = Html.page(result);
        assertThat(page.title()).as("the agreed title").isEqualTo("You do not have permission to view this page - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("the agreed heading").isEqualTo("You do not have permission to view this page");
        assertThat(page.select("main p").first().text()).as("the agreed text").isEqualTo("This part of the site is for Sunrise Letting Property Managers only.");
        assertThat(page.select("main a[href=/properties]").text()).as("the way out").isEqualTo("Find properties to let");
        assertThat(page.select(".area-band")).as("the band is for Property Managers only").isEmpty();
        assertThat(page.select("header nav li").eachText()).as("the Visitor's signed-in header").containsExactly("Properties to let", "Sign out");
        assertThat(page.select("header form.sign-out input[name=_csrf]").attr("value")).as("sign out still works from here").isNotBlank();
        assertThat(session.isInvalid()).as("the session is unchanged").isFalse();
    }

    @Test
    void aVisitorPostingToTheManagerAreaWithABadTokenGetsTheSamePage() {
        MvcTestResult result = mvc.post().uri("/manage/anything").with(authentication(visitor())).exchange();

        assertThat(result).as("a Visitor is denied").hasStatus(HttpStatus.FORBIDDEN);
        Document page = Html.page(result);
        assertThat(page.select("main h1").text()).as("the full page, not an empty 403").isEqualTo("You do not have permission to view this page");
        assertThat(page.select("header form.sign-out")).as("the signed-in header").hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/manage", "/manage/anything" })
    void aPropertyManagerIsLetThrough(String path) {
        Authentication manager = UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROPERTY_MANAGER")));

        MvcTestResult result = mvc.get().uri(path).with(authentication(manager)).exchange();

        assertThat(result).as("the rule lets them through, to a 404 because this test loads no Property Manager pages").hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void thereIsNoDefaultLoginPage() {
        MvcTestResult result = mvc.get().uri("/login").exchange();

        assertThat(result).as("Spring Security's default login page should be off").hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void thereIsNoHttpBasic() {
        MvcTestResult result = mvc.get().uri("/").header("Authorization", "Basic dXNlcjpwYXNz").exchange();

        assertThat(result).as("HTTP Basic credentials should be ignored").hasStatusOk();
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).as("there should be no Basic challenge").isNull();
    }

    @Test
    void thereIsNoDefaultLogoutEndpoint() {
        MvcTestResult result = mvc.post().uri("/logout").with(csrf()).exchange();

        assertThat(result).as("sign out comes with #2, so Spring Security's default should be off").hasStatus(HttpStatus.NOT_FOUND);
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
