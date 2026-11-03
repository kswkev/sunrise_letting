package com.sunriseletting.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Map;

import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.RememberMeLogin;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.testsupport.Html;
import com.sunriseletting.web.testsupport.RecordingExchangeGate;
import jakarta.servlet.http.HttpSession;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(SignInController.class)
@Import({ SecurityConfiguration.class, SignInExpiredFormResponder.class, RememberMeLogin.class, RecordingExchangeGate.class })
class SignInControllerTest {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String TIMED_OUT_MESSAGE = "You were signed out because you were inactive";

    private static final AuthenticationResult SIGNED_IN = new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdentityClient identityClient;

    // GET: the page

    @Test
    void pageHasTheTitleHeadingAndFormInTheSpecsOrder() {
        Document page = getPage("/sign-in");

        assertThat(page.title()).as("the title should name the page").isEqualTo("Sign in - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("the heading should name the page").isEqualTo("Sign in");
        assertThat(page.select(".error-summary")).as("no error summary on first load").isEmpty();
        assertThat(page.select(".notification-banner")).as("no banner on a first load").isEmpty();
        assertThat(page.select("header nav a[aria-current=page]").eachText()).as("Sign in should be current").containsExactly("Sign in");
        Element form = page.selectFirst("form#sign-in-form");
        assertThat(form.attr("method")).as("the form should POST").isEqualToIgnoringCase("post");
        assertThat(form.attr("action")).as("the form should post to the page").isEqualTo("/sign-in");
        assertThat(form.hasAttr("novalidate")).as("the server reports errors, not the browser").isTrue();
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the form should carry a CSRF token").isNotBlank();
        assertThat(form.select("input[name=returnTo]")).as("no return address unless one was given").isEmpty();
        assertThat(form.select("input[name=timedOut]")).as("no marker unless one was given").isEmpty();
        assertThat(form.select("label").eachText()).as("fields should be in the spec's order").containsExactly("Email address", "Password", "Remember me");
        assertThat(form.select("button[type=submit].button").text()).as("the submit button has the agreed text").isEqualTo("Sign in");
        assertThat(page.select("[required], [maxlength]")).as("the server reports these errors itself").isEmpty();
    }

    @Test
    void fieldsHaveTheSpecsAttributes() {
        Document page = getPage("/sign-in");

        Element email = page.selectFirst("#field-email input#email");
        assertThat(email.attr("name")).as("the email name").isEqualTo("email");
        assertThat(email.attr("type")).as("the email type").isEqualTo("email");
        assertThat(email.attr("autocomplete")).as("password managers pair username with the password").isEqualTo("username");
        assertThat(email.attr("spellcheck")).as("email spellcheck").isEqualTo("false");
        assertThat(email.attr("autocapitalize")).as("email autocapitalize").isEqualTo("none");

        Element password = page.selectFirst("#field-password input#password");
        assertThat(password.attr("name")).as("the password name").isEqualTo("password");
        assertThat(password.attr("type")).as("the password is masked").isEqualTo("password");
        assertThat(password.attr("autocomplete")).as("this is the current password").isEqualTo("current-password");
        assertThat(password.hasAttr("value")).as("the password is never rendered").isFalse();
        assertThat(page.select("[hx-post]")).as("no inline validation on this page").isEmpty();
        assertThat(page.select("#password-error-slot")).as("the error slot is always there").hasSize(1);
        assertThat(page.select(".password-field__toggle[aria-controls=password]").text()).as("the toggle's name").isEqualTo("Show password");
        assertThat(page.selectFirst("head script[src=/js/password-toggle.js]").hasAttr("defer")).as("the password script is deferred").isTrue();
    }

    @Test
    void rememberMeIsUntickedWithTheSpecsHint() {
        Document page = getPage("/sign-in");

        Element checkbox = page.selectFirst(".form-group.checkbox input#remember-me.checkbox__input[type=checkbox]");
        assertThat(checkbox.attr("name")).as("the checkbox name").isEqualTo("remember-me");
        assertThat(checkbox.attr("value")).as("the checkbox value").isEqualTo("on");
        assertThat(checkbox.hasAttr("checked")).as("unticked by default").isFalse();
        assertThat(checkbox.attr("aria-describedby")).as("the hint describes the checkbox").isEqualTo("remember-me-hint");
        assertThat(page.select("p.hint.checkbox__hint#remember-me-hint").text()).as("the hint should match the spec exactly")
                .isEqualTo("Stay signed in on this device for 30 days. Don't use this on a shared computer.");
    }

    @Test
    void noAccountLinkGoesToCreateAccount() {
        Document page = getPage("/sign-in");

        assertThat(page.select("main p:contains(No account yet?)").text()).as("the line should match the spec")
                .isEqualTo("No account yet? Create an account");
        assertThat(page.select("main a:contains(Create an account)").attr("href")).as("the link goes to sign up").isEqualTo("/create-account");
    }

    @Test
    void validReturnToIsKeptInTheFormAndOnTheCreateAccountLink() {
        Document page = getPage("/sign-in?returnTo=/properties/123");

        assertThat(page.select("form#sign-in-form input[type=hidden][name=returnTo]").attr("value")).as("the return address should be kept")
                .isEqualTo("/properties/123");
        assertThat(page.select("main a:contains(Create an account)").attr("href")).as("the link passes it on")
                .isEqualTo("/create-account?returnTo=/properties/123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)" })
    void unsafeReturnToIsLeftOut(String returnTo) {
        MvcTestResult result = mvc.get().uri("/sign-in").param("returnTo", returnTo).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("input[name=returnTo]")).as("an unsafe return address shouldn't be in the form").isEmpty();
        assertThat(page.select("main a:contains(Create an account)").attr("href")).as("the link shouldn't carry it").isEqualTo("/create-account");
    }

    @Test
    void timedOutShowsTheInfoBannerAboveTheHeadingAndKeepsTheMarker() {
        Document page = getPage("/sign-in?timedOut=true&returnTo=/properties/123");

        Element banner = page.selectFirst("main .notification-banner.notification-banner--info[role=status][autofocus]");
        assertThat(banner).as("the information banner should be shown and focused").isNotNull();
        assertThat(banner.select("h2").text()).as("the banner has the agreed copy").isEqualTo(TIMED_OUT_MESSAGE);
        assertThat(banner.nextElementSibling().tagName()).as("the banner sits above the heading").isEqualTo("h1");
        assertThat(page.select("input[type=hidden][name=timedOut]").attr("value")).as("the marker is kept for a failed submit").isEqualTo("true");
        assertThat(page.select("input[name=returnTo]").attr("value")).as("the return address is kept").isEqualTo("/properties/123");
    }

    @Test
    void anyOtherTimedOutValueIsIgnored() {
        Document page = getPage("/sign-in?timedOut=yes");

        assertThat(page.select(".notification-banner")).as("only the agreed marker shows the banner").isEmpty();
        assertThat(page.select("input[name=timedOut]")).as("only the agreed marker is kept").isEmpty();
    }

    // GET: already signed in

    @Test
    void getWhenSignedInRedirectsToPropertiesWithNoForm() {
        MvcTestResult result = mvc.get().uri("/sign-in?returnTo=/properties/123").with(authentication(visitor())).exchange();

        assertThat(result).as("a signed-in Visitor should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("with no marker the return address is ignored").hasRedirectedUrl("/properties");
        assertThat(Html.body(result)).as("no form should be shown").doesNotContain("sign-in-form");
    }

    @Test
    void timedOutWhenSignedInGoesStraightBackToTheReturnAddress() {
        MvcTestResult result = mvc.get().uri("/sign-in").param("timedOut", "true").param("returnTo", "/properties/123?q={leeds}")
                .with(authentication(visitor())).exchange();

        assertThat(result).as("the tab should be sent straight back").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("it should go to the return address").hasRedirectedUrl("/properties/123?q={leeds}");
        assertThat(Html.body(result)).as("no message and no form").doesNotContain(TIMED_OUT_MESSAGE).doesNotContain("sign-in-form");
    }

    @Test
    void timedOutWhenSignedInWithAnUnsafeReturnAddressGoesToProperties() {
        MvcTestResult result = mvc.get().uri("/sign-in").param("timedOut", "true").param("returnTo", "//evil.example")
                .with(authentication(visitor())).exchange();

        assertThat(result).as("the tab should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("an unsafe address is ignored").hasRedirectedUrl("/properties");
    }

    // POST: success

    @Test
    void correctDetailsWithAReturnAddressSignInAndGoBackToIt() {
        when(identityClient.authenticate(" Jo@Example.com ", SECRET)).thenReturn(SIGNED_IN);

        MvcTestResult result = submit(" Jo@Example.com ", SECRET).param("returnTo", "/properties/123").with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("the Visitor goes back to the page they came from").hasRedirectedUrl("/properties/123");
        assertSignedInAsVisitor(result);
        verify(identityClient, description("identity should get the details as typed, to trim and match itself")).authenticate(" Jo@Example.com ", SECRET);
    }

    @Test
    void correctDetailsWithNoReturnAddressGoToProperties() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(SIGNED_IN);

        MvcTestResult result = submit("jo@example.com", SECRET).with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("with no return address the Visitor goes to the search page").hasRedirectedUrl("/properties");
        assertSignedInAsVisitor(result);
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)" })
    void correctDetailsWithAnUnsafeReturnAddressGoToProperties(String returnTo) {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(SIGNED_IN);

        MvcTestResult result = submit("jo@example.com", SECRET).param("returnTo", returnTo).with(csrf()).exchange();

        assertThat(result).as("an unsafe address is ignored").hasRedirectedUrl("/properties");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void theRedirectNeverCarriesThePassword() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(SIGNED_IN);

        MvcTestResult result = submit("jo@example.com", SECRET).with(csrf()).exchange();

        assertThat(result.getResponse().getRedirectedUrl()).as("nothing from the form should reach the address").isEqualTo("/properties");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void postWhenAlreadySignedInRedirectsAndChecksNothing() {
        MvcTestResult result = submit("jo@example.com", SECRET).with(authentication(visitor())).with(csrf()).exchange();

        assertThat(result).as("a signed-in Visitor should be redirected").hasRedirectedUrl("/properties");
        verify(identityClient, never().description("identity shouldn't be asked")).authenticate(any(), any());
    }

    // POST: failures

    @Test
    void invalidCredentialsKeepTheEmailAndShowOneGenericError() {
        when(identityClient.authenticate(" Jo@Example.com ", SECRET)).thenReturn(AuthenticationResult.invalidCredentials());

        MvcTestResult result = submit(" Jo@Example.com ", SECRET).param("remember-me", "on").with(csrf()).exchange();

        assertThat(result).as("wrong details re-show the page with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title gets the error prefix").isEqualTo("Error: Sign in - Sunrise Letting");
        assertThat(page.select(".error-summary[autofocus] p.error-summary__body").text()).as("one generic error")
                .isEqualTo("The email address or password is incorrect.");
        assertThat(page.select(".form-group--error")).as("no field is marked as wrong").isEmpty();
        assertThat(page.selectFirst("#email").attr("value")).as("the email is kept exactly as typed").isEqualTo(" Jo@Example.com ");
        assertNoPassword(result);
        assertThat(page.selectFirst("#remember-me").hasAttr("checked")).as("Remember me keeps its state").isTrue();
        assertNotSignedIn(result);
        verify(identityClient, description("identity should be asked")).authenticate(" Jo@Example.com ", SECRET);
    }

    @Test
    void invalidCredentialsWithTheMarkerKeepTheBannerButTheSummaryTakesFocus() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(AuthenticationResult.invalidCredentials());

        MvcTestResult result = submit("jo@example.com", SECRET).param("timedOut", "true").param("returnTo", "/properties/123").with(csrf()).exchange();

        Document page = Html.page(result);
        Element banner = page.selectFirst(".notification-banner--info");
        assertThat(banner).as("the banner stays").isNotNull();
        assertThat(banner.hasAttr("autofocus")).as("the banner gives up autofocus").isFalse();
        assertThat(page.select(".error-summary[autofocus]")).as("the summary takes focus").hasSize(1);
        assertThat(banner.nextElementSibling().hasClass("error-summary")).as("the banner sits above the summary").isTrue();
        assertThat(page.select("input[name=timedOut]").attr("value")).as("the marker is still carried").isEqualTo("true");
        assertThat(page.select("input[name=returnTo]").attr("value")).as("the return address is still carried").isEqualTo("/properties/123");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void bothFieldsEmptyShowFieldErrorsWithSummaryLinks() {
        when(identityClient.authenticate("", "")).thenReturn(new AuthenticationResult.FieldErrors(
                Map.of("email", "EMAIL_REQUIRED", "password", "PASSWORD_REQUIRED")));

        MvcTestResult result = submit("", "").with(csrf()).exchange();

        assertThat(result).as("empty fields re-show the page with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title gets the error prefix").isEqualTo("Error: Sign in - Sunrise Letting");
        assertThat(page.select(".error-summary ul li a").eachText()).as("the summary lists the fields in order")
                .containsExactly("Enter your email address", "Enter your password");
        assertThat(page.select(".error-summary ul li a").eachAttr("href")).as("the links go to the inputs").containsExactly("#email", "#password");
        assertThat(page.select("#field-email.form-group--error #email-error").text()).as("the email's message").isEqualTo("Error: Enter your email address");
        assertThat(page.select("#field-password.form-group--error #password-error-slot #password-error").text()).as("the password's message")
                .isEqualTo("Error: Enter your password");
        assertThat(page.selectFirst("#email").attr("aria-invalid")).as("the email is marked invalid").isEqualTo("true");
        assertThat(page.selectFirst("#password").attr("aria-invalid")).as("the password is marked invalid").isEqualTo("true");
        assertNotSignedIn(result);
        verify(identityClient, description("identity decides what is empty")).authenticate("", "");
    }

    @Test
    void onlyThePasswordEmptyShowsOnlyItsErrorAndKeepsTheEmail() {
        when(identityClient.authenticate("jo@example.com", "")).thenReturn(new AuthenticationResult.FieldErrors(Map.of("password", "PASSWORD_REQUIRED")));

        Document page = Html.page(submit("jo@example.com", "").with(csrf()).exchange());

        assertThat(page.select(".error-summary ul li a").eachText()).as("only the password is listed").containsExactly("Enter your password");
        assertThat(page.select("#field-email.form-group--error")).as("the email isn't marked").isEmpty();
        assertThat(page.selectFirst("#email").attr("value")).as("the email is kept").isEqualTo("jo@example.com");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", "");
    }

    @Test
    void aMissingFieldIsSentAsEmpty() {
        when(identityClient.authenticate("", "")).thenReturn(new AuthenticationResult.FieldErrors(Map.of("email", "EMAIL_REQUIRED")));

        MvcTestResult result = mvc.post().uri("/sign-in").with(csrf()).exchange();

        assertThat(result).as("a POST without the fields is treated as empty fields").hasStatusOk();
        verify(identityClient, description("missing fields should reach identity as empty")).authenticate("", "");
    }

    @Test
    void identityUnavailableAnswers503WithTheFormLevelMessage() {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(AuthenticationResult.unavailable());

        MvcTestResult result = submit("jo@example.com", SECRET).param("remember-me", "on").param("timedOut", "true").with(csrf()).exchange();

        assertThat(result).as("a failing identity gives 503").hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        Document page = Html.page(result);
        assertThat(page.title()).as("the title gets the error prefix").isEqualTo("Error: Sign in - Sunrise Letting");
        assertThat(page.select(".error-summary p.error-summary__body").text()).as("the agreed message")
                .isEqualTo("Sorry, you can't sign in at the moment. Try again later.");
        assertThat(page.selectFirst("#email").attr("value")).as("the email is kept").isEqualTo("jo@example.com");
        assertNoPassword(result);
        assertThat(page.selectFirst("#remember-me").hasAttr("checked")).as("Remember me keeps its state").isTrue();
        assertThat(page.select(".notification-banner--info:not([autofocus])")).as("the banner stays, without focus").hasSize(1);
        assertNotSignedIn(result);
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    // POST: form expired

    @Test
    void missingCsrfTokenReshowsTheFormAsExpired() {
        assertFormExpired(submit(" Jo@Example.com ", SECRET).param("remember-me", "on").param("returnTo", "/properties/123").exchange());
    }

    @Test
    void invalidCsrfTokenReshowsTheFormAsExpired() {
        assertFormExpired(submit(" Jo@Example.com ", SECRET).param("remember-me", "on").param("returnTo", "/properties/123")
                .with(csrf().useInvalidToken()).exchange());
    }

    @Test
    void expiredFormWithTheMarkerShowsOnlyTheExpiredErrorAndDropsTheMarker() {
        MvcTestResult result = submit(" Jo@Example.com ", SECRET).param("remember-me", "on").param("returnTo", "/properties/123")
                .param("timedOut", "true").exchange();

        assertFormExpired(result);
        Document page = Html.page(result);
        assertThat(page.select(".notification-banner--info")).as("no inactivity banner with an expired form").isEmpty();
        assertThat(Html.body(result)).as("no inactivity message with an expired form").doesNotContain(TIMED_OUT_MESSAGE);
        assertThat(page.select("input[name=timedOut]")).as("the marker is dropped, so the banner doesn't come back").isEmpty();
    }

    @Test
    void expiredFormDropsAnUnsafeReturnAddress() {
        Document page = Html.page(submit("jo@example.com", SECRET).param("returnTo", "//evil.example").exchange());

        assertThat(page.select("input[name=returnTo]")).as("an unsafe address isn't kept").isEmpty();
    }

    @Test
    void expiredFormWhenAlreadySignedInRedirects() {
        MvcTestResult result = submit("jo@example.com", SECRET).with(authentication(visitor())).exchange();

        assertThat(result).as("a signed-in Visitor doesn't need the form").hasRedirectedUrl("/properties");
        verify(identityClient, never().description("identity shouldn't be asked")).authenticate(any(), any());
    }

    private void assertFormExpired(MvcTestResult result) {
        assertThat(result).as("an expired form re-renders with 200, not 403").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title gets the error prefix").isEqualTo("Error: Sign in - Sunrise Letting");
        assertThat(page.select(".error-summary[autofocus] p.error-summary__body").text()).as("the agreed message")
                .isEqualTo("Your form has expired. Check your details and submit again.");
        assertThat(page.select(".form-group--error")).as("no field is marked as wrong").isEmpty();
        assertThat(page.selectFirst("#email").attr("value")).as("the email is kept as typed").isEqualTo(" Jo@Example.com ");
        assertThat(page.selectFirst("#remember-me").hasAttr("checked")).as("Remember me keeps its state").isTrue();
        assertThat(page.select("input[type=hidden][name=returnTo]").attr("value")).as("the return address is kept").isEqualTo("/properties/123");
        assertThat(page.select("input[name=_csrf]").attr("value")).as("a new token is on the form").isNotBlank();
        assertNoPassword(result);
        assertNotSignedIn(result);
        verify(identityClient, never().description("the details aren't checked")).authenticate(any(), any());
    }

    private void assertNoPassword(MvcTestResult result) {
        assertThat(Html.page(result).selectFirst("#password").hasAttr("value")).as("the password input has no value").isFalse();
        assertThat(Html.body(result)).as("the response never contains the password").doesNotContain(SECRET);
    }

    private void assertSignedInAsVisitor(MvcTestResult result) {
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).as("there should be a session").isNotNull();
        assertThat(session.getMaxInactiveInterval()).as("a Visitor's session idles out after 30 minutes").isEqualTo(1800);
        SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).as("the session should hold the security context").isNotNull();
        assertThat(context.getAuthentication().getPrincipal()).as("the Visitor should be the principal")
                .isEqualTo(new SessionPrincipal("account-1", AccountRole.VISITOR));
    }

    private void assertNotSignedIn(MvcTestResult result) {
        HttpSession session = result.getRequest().getSession(false);
        Object context = session == null ? null : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).as("nobody should be signed in").isNull();
    }

    private MockMvcRequestBuilder submit(String email, String password) {
        return mvc.post().uri("/sign-in").param("email", email).param("password", password);
    }

    private Document getPage(String uri) {
        MvcTestResult result = mvc.get().uri(uri).exchange();
        assertThat(result).as("page at " + uri + " should load").hasStatusOk();
        return Html.page(result);
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
