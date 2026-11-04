package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.sunriseletting.web.account.SignInController;
import com.sunriseletting.web.account.SignOutController;
import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.identity.RememberMeTokenResult;
import com.sunriseletting.web.identity.RememberMeVerifyResult;
import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.testsupport.Html;
import com.sunriseletting.web.testsupport.RecordingExchangeGate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({ SignInController.class, SignOutController.class, PropertiesController.class })
@Import({ SecurityConfiguration.class, RememberMeLogin.class, RecordingExchangeGate.class })
class RememberMeLoginTest {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String SERIES = "A".repeat(22);

    private static final String TOKEN = "B".repeat(43);

    private static final String NEW_TOKEN = "C".repeat(43);

    private static final String COOKIE_VALUE = SERIES + "." + TOKEN;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private RecordingExchangeGate gate;

    @MockitoBean
    private IdentityClient identityClient;

    @BeforeEach
    void openTheGate() {
        gate.reset();
    }

    // Signing in with the box ticked

    @Test
    void signingInWithTheBoxTickedSetsTheCookieWithMaxAgeFromExpiresAt() {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(SERIES, TOKEN, expiresAt));

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat(result).as("the Visitor should be signed in").hasStatus(HttpStatus.SEE_OTHER);
        String setCookie = setCookie(result);
        assertThat(setCookie).as("the cookie holds the series and token").startsWith("remember-me=" + COOKIE_VALUE + ";");
        assertThat(setCookie).as("scripts can't read it").contains("HttpOnly");
        assertThat(setCookie).as("it only travels over HTTPS").contains("Secure");
        assertThat(setCookie).as("it isn't sent on cross-site POSTs").contains("SameSite=Lax");
        assertThat(setCookie).as("the whole site can read it").contains("Path=/");
        assertThat(maxAge(setCookie)).as("it should last as long as the token").isCloseTo(Duration.ofDays(30).toSeconds(), within(60L));
        assertThat(result.getRequest().getSession(false).getAttribute(RememberMeLogin.SESSION_ATTRIBUTE)).as("the session is flagged").isEqualTo(true);
        verify(identityClient, description("identity should issue the token")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void signingInWithoutTheBoxSetsNoCookieAndIssuesNothing() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));

        MvcTestResult result = signIn().with(csrf()).exchange();

        assertThat(result).as("the Visitor should be signed in").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(setCookie(result)).as("there should be no remember-me cookie").isNull();
        assertThat(result.getRequest().getSession(false).getAttribute(RememberMeLogin.SESSION_ATTRIBUTE)).as("the session isn't flagged").isNull();
        verify(identityClient, never().description("no token should be issued")).issueRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aPropertyManagerTickingTheBoxGetsNoToken() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-2", AccountRole.PROPERTY_MANAGER, false));

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat(setCookie(result)).as("Remember me is ignored for Property Managers").isNull();
        verify(identityClient, never().description("no token should be issued")).issueRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void whenNoTokenCanBeIssuedTheVisitorIsStillSignedInWithAnOrdinarySession() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(RememberMeTokenResult.unavailable());

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat(result).as("the Visitor should still be signed in").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(setCookie(result)).as("there's no token to put in a cookie").isNull();
        assertThat(result.getRequest().getSession(false).getAttribute(RememberMeLogin.SESSION_ATTRIBUTE))
                .as("the session can time out, so the warning must show").isNull();
        verify(identityClient, description("identity should have been asked")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void signingInWithTheBoxTickedKeepsTheSessionForTheLifeOfTheLogin() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(SERIES, TOKEN, Instant.now().plus(Duration.ofDays(30))));

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat((long) result.getRequest().getSession(false).getMaxInactiveInterval()).as("the session shouldn't idle out before the login ends")
                .isCloseTo(Duration.ofDays(30).toSeconds(), within(60L));
        verify(identityClient, description("identity should issue the token")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aSessionIsNeverKeptPastTheEndOfTheLogin() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(SERIES, TOKEN, Instant.now().plus(Duration.ofMinutes(10))));

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat((long) result.getRequest().getSession(false).getMaxInactiveInterval()).as("the session should end with the login")
                .isCloseTo(Duration.ofMinutes(10).toSeconds(), within(60L));
        verify(identityClient, description("identity should issue the token")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void signingInWithoutTheBoxKeepsTheOrdinaryTimeOut() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));

        MvcTestResult result = signIn().with(csrf()).exchange();

        assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).as("other sessions don't get longer").isEqualTo(1800);
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void whenNoTokenCanBeIssuedTheSessionKeepsTheOrdinaryTimeOut() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(RememberMeTokenResult.unavailable());

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).as("with no login there's nothing to keep the session for").isEqualTo(1800);
        verify(identityClient, description("identity should have been asked")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aPropertyManagerTickingTheBoxKeepsTheOrdinaryTimeOut() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-2", AccountRole.PROPERTY_MANAGER, false));

        MvcTestResult result = signIn().param("remember-me", "on").with(csrf()).exchange();

        assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).as("Remember me is ignored for Property Managers").isEqualTo(7200);
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void exchangingTheCookieKeepsTheSessionForWhatIsLeftOfTheLogin() {
        when(identityClient.verifyRememberMeToken(SERIES, TOKEN))
                .thenReturn(new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, NEW_TOKEN, Instant.now().plus(Duration.ofDays(10))));

        MvcTestResult result = page().exchange();

        assertThat((long) result.getRequest().getSession(false).getMaxInactiveInterval()).as("the session should last as long as the login has left, not a fresh 30 days")
                .isCloseTo(Duration.ofDays(10).toSeconds(), within(60L));
        verify(identityClient, description("the cookie should be verified")).verifyRememberMeToken(SERIES, TOKEN);
    }
    // Signing in over a cookie left by an earlier login

    @Test
    void signingInWithoutTheBoxClearsAnEarlierCookieAndRevokesItsSeries() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));

        MvcTestResult result = signIn().cookie(new Cookie("remember-me", COOKIE_VALUE)).with(csrf()).exchange();

        assertThat(result).as("the Visitor should be signed in").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(setCookie(result)).as("the earlier login's cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, description("the earlier login should be revoked")).revokeRememberMeToken(SERIES);
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void signingInWithoutTheBoxClearsAMalformedCookieAndRevokesNothing() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));

        MvcTestResult result = signIn().cookie(new Cookie("remember-me", "nonsense")).with(csrf()).exchange();

        assertThat(setCookie(result)).as("the cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void signingInWithoutTheBoxAndWithoutACookieRevokesNothing() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));

        MvcTestResult result = signIn().with(csrf()).exchange();

        assertThat(setCookie(result)).as("there's nothing to clear").isNull();
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aNewLoginReplacesTheCookieAndRevokesTheOldSeries() {
        String newSeries = "D".repeat(22);
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(newSeries, NEW_TOKEN, Instant.now().plus(Duration.ofDays(30))));

        MvcTestResult result = signIn().param("remember-me", "on").cookie(new Cookie("remember-me", COOKIE_VALUE)).with(csrf()).exchange();

        assertThat(result.getResponse().getHeaders("Set-Cookie").stream().filter(header -> header.startsWith("remember-me=")))
                .as("the browser should get only the new cookie").hasSize(1);
        assertThat(setCookie(result)).as("the cookie holds the new login").startsWith("remember-me=" + newSeries + "." + NEW_TOKEN + ";");
        verify(identityClient, description("the old login should be revoked")).revokeRememberMeToken(SERIES);
        verify(identityClient, never().description("the new login stays good")).revokeRememberMeToken(newSeries);
        verify(identityClient, description("identity should issue the token")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aNewLoginOverAMalformedCookieRevokesNothing() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(SERIES, NEW_TOKEN, Instant.now().plus(Duration.ofDays(30))));

        MvcTestResult result = signIn().param("remember-me", "on").cookie(new Cookie("remember-me", "nonsense")).with(csrf()).exchange();

        assertThat(setCookie(result)).as("the cookie holds the new login").startsWith("remember-me=" + SERIES + "." + NEW_TOKEN + ";");
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
        verify(identityClient, description("identity should issue the token")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void whenNoNewTokenCanBeIssuedAnEarlierCookieIsStillClearedAndRevoked() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(RememberMeTokenResult.unavailable());

        MvcTestResult result = signIn().param("remember-me", "on").cookie(new Cookie("remember-me", COOKIE_VALUE)).with(csrf()).exchange();

        assertThat(result).as("the Visitor should still be signed in").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(setCookie(result)).as("the earlier login's cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, description("the earlier login should be revoked")).revokeRememberMeToken(SERIES);
        verify(identityClient, description("identity should have been asked")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aPropertyManagerSigningInWithTheBoxClearsAnEarlierCookieAndRevokesItsSeries() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-2", AccountRole.PROPERTY_MANAGER, false));

        MvcTestResult result = signIn().param("remember-me", "on").cookie(new Cookie("remember-me", COOKIE_VALUE)).with(csrf()).exchange();

        assertThat(setCookie(result)).as("the earlier login's cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, description("the earlier login should be revoked")).revokeRememberMeToken(SERIES);
        verify(identityClient, never().description("Remember me is ignored for Property Managers")).issueRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void aFailedSignInLeavesTheCookieAlone() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(AuthenticationResult.invalidCredentials());

        MvcTestResult result = signIn().cookie(new Cookie("remember-me", COOKIE_VALUE)).with(csrf()).exchange();

        assertThat(result).as("the page should show the error").hasStatusOk();
        assertThat(setCookie(result)).as("nobody signed in, so the cookie stays").isNull();
        verify(identityClient, never().description("nobody signed in, so nothing is revoked")).revokeRememberMeToken(any());
        verify(identityClient, description("identity should check the details")).authenticate("jo@example.com", SECRET);
    }

    // Coming back with only the cookie

    @Test
    void aValidCookieSignsInSilentlyAndReplacesTheCookie() {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(10));
        when(identityClient.verifyRememberMeToken(SERIES, TOKEN)).thenReturn(new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, NEW_TOKEN, expiresAt));

        MvcTestResult result = page().exchange();

        assertThat(result).as("no redirect: the page just loads").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("header form.sign-out")).as("this very page shows the signed-in header").hasSize(1);
        assertThat(page.select(".notification-banner")).as("no message").isEmpty();
        HttpSession session = result.getRequest().getSession(false);
        SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).as("the Visitor is signed in").isEqualTo(new SessionPrincipal("account-1", AccountRole.VISITOR, false));
        assertThat(session.getAttribute(RememberMeLogin.SESSION_ATTRIBUTE)).as("the session is flagged").isEqualTo(true);
        assertThat(setCookie(result)).as("the cookie holds the new token").startsWith("remember-me=" + SERIES + "." + NEW_TOKEN + ";");
        assertThat(maxAge(setCookie(result))).as("the expiry is fixed, so Max-Age comes from expiresAt, not a fresh 30 days")
                .isCloseTo(Duration.ofDays(10).toSeconds(), within(60L));
        verify(identityClient, times(1).description("the cookie should be verified once")).verifyRememberMeToken(SERIES, TOKEN);
    }

    @Test
    void anInvalidCookieIsClearedAndTheRequestIsAnonymous() {
        when(identityClient.verifyRememberMeToken(SERIES, TOKEN)).thenReturn(RememberMeVerifyResult.invalid());

        MvcTestResult result = page().exchange();

        assertThat(result).as("the page still loads").hasStatusOk();
        assertThat(Html.page(result).select("header a[href=/sign-in]")).as("the request is signed out").hasSize(1);
        assertThat(setCookie(result)).as("the cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, description("identity should have been asked")).verifyRememberMeToken(SERIES, TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = { "nonsense", "only-a-series.", ".only-a-token", "AAA.BBB", "a.b.c" })
    void aMalformedCookieIsClearedWithoutAskingIdentity(String value) {
        MvcTestResult result = mvc.get().uri("/properties").cookie(new Cookie("remember-me", value)).exchange();

        assertThat(setCookie(result)).as("the cookie is cleared").contains("Max-Age=0");
        verify(identityClient, never().description("anything else isn't worth a call")).verifyRememberMeToken(any(), any());
    }

    @Test
    void identityUnavailableLeavesTheRequestAnonymousAndKeepsTheCookie() {
        when(identityClient.verifyRememberMeToken(SERIES, TOKEN)).thenReturn(RememberMeVerifyResult.unavailable());

        MvcTestResult result = page().exchange();

        assertThat(result).as("the page still loads").hasStatusOk();
        assertThat(Html.page(result).select("header a[href=/sign-in]")).as("the request is signed out").hasSize(1);
        assertThat(setCookie(result)).as("the cookie is kept").isNull();
        assertThat(gate.reopened()).as("the next request may try again").containsExactly(SERIES);
        verify(identityClient, description("identity should have been asked")).verifyRememberMeToken(SERIES, TOKEN);
    }

    // Exchanged once per browser

    @Test
    void aShutGateLeavesTheRequestAnonymousAndNeverReachesIdentity() {
        gate.shut();

        MvcTestResult result = page().exchange();

        assertThat(result).as("the page still loads").hasStatusOk();
        assertThat(Html.page(result).select("header a[href=/sign-in]")).as("this request is signed out").hasSize(1);
        assertThat(setCookie(result)).as("the cookie is left alone").isNull();
        verify(identityClient, never().description("a parallel request must not spend the token")).verifyRememberMeToken(any(), any());
    }

    @Test
    void anHtmxRequestDoesNotExchangeTheCookie() {
        MvcTestResult result = page().header("HX-Request", "true").exchange();

        assertThat(setCookie(result)).as("the cookie is left alone").isNull();
        verify(identityClient, never().description("only full-page requests exchange")).verifyRememberMeToken(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "image", "script", "style", "empty", "iframe" })
    void aSubresourceRequestDoesNotExchangeTheCookie(String destination) {
        page().header("Sec-Fetch-Dest", destination).exchange();

        verify(identityClient, never().description("only full-page requests exchange")).verifyRememberMeToken(any(), any());
    }

    @Test
    void aPrefetchDoesNotExchangeTheCookie() {
        page().header("Sec-Fetch-Dest", "document").header("Sec-Purpose", "prefetch").exchange();

        verify(identityClient, never().description("nobody may ever see a prefetch's response")).verifyRememberMeToken(any(), any());
    }

    @Test
    void aDocumentNavigationDoesExchangeTheCookie() {
        when(identityClient.verifyRememberMeToken(SERIES, TOKEN)).thenReturn(RememberMeVerifyResult.invalid());

        page().header("Sec-Fetch-Dest", "document").exchange();

        verify(identityClient, description("a real navigation exchanges")).verifyRememberMeToken(SERIES, TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/css/site.css", "/js/password-toggle.js", "/webjars/htmx.org/dist/htmx.min.js" })
    void staticFilesDoNotExchangeTheCookie(String path) {
        mvc.get().uri(path).cookie(new Cookie("remember-me", COOKIE_VALUE)).exchange();

        verify(identityClient, never().description("only full-page requests exchange")).verifyRememberMeToken(any(), any());
    }

    @Test
    void aPostDoesNotExchangeTheCookie() {
        mvc.post().uri("/sign-in").param("email", "").param("password", "").cookie(new Cookie("remember-me", COOKIE_VALUE)).exchange();

        verify(identityClient, never().description("only full-page requests exchange")).verifyRememberMeToken(any(), any());
    }

    @Test
    void aSignedInRequestDoesNotExchangeTheCookie() {
        page().with(authentication(visitor())).exchange();

        verify(identityClient, never().description("a live session has no need of the cookie")).verifyRememberMeToken(any(), any());
    }

    // Signing out

    @Test
    void signingOutRevokesTheSeriesAndClearsTheCookie() {
        MvcTestResult result = mvc.post().uri("/sign-out").session(new MockHttpSession()).with(authentication(visitor())).with(csrf())
                .cookie(new Cookie("remember-me", COOKIE_VALUE)).exchange();

        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        assertThat(setCookie(result)).as("the cookie is cleared").startsWith("remember-me=;").contains("Max-Age=0");
        verify(identityClient, description("the series should be revoked")).revokeRememberMeToken(SERIES);
    }

    @Test
    void signingOutWithAStaleTokenStillRevokesAndClears() {
        MvcTestResult result = mvc.post().uri("/sign-out").cookie(new Cookie("remember-me", COOKIE_VALUE)).exchange();

        assertThat(result).as("sign out lands on the search page").hasRedirectedUrl("/properties");
        assertThat(setCookie(result)).as("the cookie is cleared").contains("Max-Age=0");
        verify(identityClient, description("the series should be revoked")).revokeRememberMeToken(SERIES);
    }

    @Test
    void signingOutWithAMalformedCookieClearsItAndRevokesNothing() {
        MvcTestResult result = mvc.post().uri("/sign-out").with(csrf()).cookie(new Cookie("remember-me", "nonsense")).exchange();

        assertThat(setCookie(result)).as("the cookie is cleared").contains("Max-Age=0");
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
    }

    @Test
    void signingOutWithNoCookieTouchesNothing() {
        MvcTestResult result = mvc.post().uri("/sign-out").with(csrf()).exchange();

        assertThat(setCookie(result)).as("a cross-site POST carries no cookies, so nothing should be cleared").isNull();
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
    }

    private MockMvcRequestBuilder signIn() {
        return mvc.post().uri("/sign-in").param("email", "jo@example.com").param("password", SECRET);
    }

    private MockMvcRequestBuilder page() {
        return mvc.get().uri("/properties").cookie(new Cookie("remember-me", COOKIE_VALUE));
    }

    private String setCookie(MvcTestResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream().filter(header -> header.startsWith("remember-me=")).findFirst().orElse(null);
    }

    private long maxAge(String setCookie) {
        String after = setCookie.substring(setCookie.indexOf("Max-Age=") + "Max-Age=".length());
        return Long.parseLong(after.substring(0, after.indexOf(';')));
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
