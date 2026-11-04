package com.sunriseletting.web.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.Map;

import com.sunriseletting.web.identity.ChangePasswordResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.testsupport.Html;
import com.sunriseletting.web.testsupport.SignedInUsers;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({ ChangePasswordController.class, ManageController.class })
@Import(SecurityConfiguration.class)
class ChangePasswordControllerTest {

    private static final String PATH = "/manage/change-password";

    private static final String SECRET = "N3w-Secret-Pa55!";

    private static final String OTHER_SECRET = "0ther-Secret-Pa55!";

    private static final String UNAVAILABLE_MESSAGE = "Sorry, we couldn't change your password. Try again later.";

    private static final String BANNER = "Your password has been changed";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdentityClient identityClient;

    // GET: the page

    @Test
    void pageHasTheTitleHeadingIntroAndFormInTheSpecsOrder() {
        Document page = temporaryPasswordPage();

        assertThat(page.title()).as("the title should name the page").isEqualTo("Change your password - Sunrise Letting");
        assertThat(page.select("main .form-column > *").eachAttr("id")).as("no summary on first load, and the form last").endsWith("change-password-form");
        assertThat(page.select(".error-summary")).as("no error summary on first load").isEmpty();
        assertThat(page.select("main h1").text()).as("the agreed heading").isEqualTo("Change your password");
        assertThat(page.selectFirst("main h1").nextElementSibling().text()).as("the intro follows the heading")
                .isEqualTo("You're signed in with a temporary password. Choose a new password to continue.");
        Element form = page.selectFirst("form#change-password-form");
        assertThat(form.attr("method")).as("the form should POST").isEqualToIgnoringCase("post");
        assertThat(form.attr("action")).as("the form should post to the page").isEqualTo(PATH);
        assertThat(form.hasAttr("novalidate")).as("the server reports errors, not the browser").isTrue();
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the form should carry a CSRF token").isNotBlank();
        assertThat(form.select("input[name=returnTo]")).as("this form has no return address").isEmpty();
        assertThat(form.select("label").eachText()).as("only the two new-password fields: the current password isn't asked for again")
                .containsExactly("New password", "Confirm new password");
        assertThat(form.select("button[type=submit].button").text()).as("the submit button has the agreed text").isEqualTo("Change password");
        assertThat(page.select("[required], [maxlength]")).as("the server reports these errors itself").isEmpty();
    }

    @Test
    void fieldsAreThePasswordComponentWithTheSpecsAttributes() {
        Document page = temporaryPasswordPage();

        Element newPassword = page.selectFirst("#field-new-password.password-field input#new-password");
        assertThat(newPassword.attr("name")).as("the name identity's field is read from").isEqualTo("newPassword");
        assertThat(newPassword.attr("type")).as("the password is masked").isEqualTo("password");
        assertThat(newPassword.attr("autocomplete")).as("password managers should offer a new password").isEqualTo("new-password");
        assertThat(newPassword.hasAttr("value")).as("the password is never rendered").isFalse();
        assertThat(page.select("#new-password-hint").text()).as("the agreed hint")
                .isEqualTo("Must be 8 to 128 characters and include an upper-case letter, a lower-case letter, a number and a symbol. It must be different from your temporary password.");
        assertThat(page.select("#new-password-error-slot")).as("the error slot is always there").hasSize(1);

        Element confirm = page.selectFirst("#field-confirm-new-password.password-field input#confirm-new-password");
        assertThat(confirm.attr("name")).as("the name identity's field is read from").isEqualTo("confirmNewPassword");
        assertThat(confirm.attr("type")).as("the password is masked").isEqualTo("password");
        assertThat(confirm.attr("autocomplete")).as("password managers should offer a new password").isEqualTo("new-password");
        assertThat(confirm.hasAttr("value")).as("the password is never rendered").isFalse();
        assertThat(page.select("#confirm-new-password-hint")).as("this field has no hint").isEmpty();
        assertThat(page.select("#confirm-new-password-error-slot")).as("the error slot is always there").hasSize(1);

        assertThat(page.select("main [hx-post], main [hx-trigger], main [hx-target], main [hx-swap], main [hx-params]")).as("no inline validation on this page").isEmpty();
    }

    @Test
    void togglesAreHiddenUntilTheScriptRunsAndHaveUniqueNames() {
        Document page = temporaryPasswordPage();

        assertThat(page.select(".password-field__toggle[aria-controls=new-password]").text()).as("the first toggle's name").isEqualTo("Show new password");
        assertThat(page.select(".password-field__toggle[aria-controls=confirm-new-password]").text()).as("the second toggle's name").isEqualTo("Show confirm new password");
        assertThat(page.select(".password-field__toggle[hidden]")).as("the script reveals both toggles").hasSize(2);
        assertThat(page.selectFirst("head script[src=/js/password-toggle.js]").hasAttr("defer")).as("the password script is on the page, deferred").isTrue();
    }

    @Test
    void pageIsInThePropertyManagerLayoutWithTheBandAndCurrentItem() {
        Document page = temporaryPasswordPage();

        assertThat(page.select("header .area-band").text()).as("the band").isEqualTo("Property Manager area");
        assertThat(page.select("header nav li").eachText()).as("the Property Manager's items").containsExactly("Properties to let", "Property Manager area", "Sign out");
        assertThat(page.select("header nav [aria-current=page]").eachText()).as("the area is current").containsExactly("Property Manager area");
    }

    @Test
    void aPermanentPasswordIsSentToTheAreaHome() {
        MvcTestResult result = mvc.get().uri(PATH).with(authentication(SignedInUsers.propertyManager(false))).exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("changing a password at other times is out of scope").hasRedirectedUrl("/manage");
    }

    @Test
    void aVisitorGetsTheAccessDeniedPage() {
        MvcTestResult result = mvc.get().uri(PATH).with(authentication(SignedInUsers.visitor())).exchange();

        assertThat(result).as("a Visitor is denied").hasStatus(HttpStatus.FORBIDDEN);
        assertThat(Html.page(result).select("main h1").text()).as("the access-denied page").isEqualTo("You do not have permission to view this page");
    }

    @Test
    void signedOutGoesToSignIn() {
        MvcTestResult result = mvc.get().uri(PATH).exchange();

        assertThat(result).as("the access rule comes first").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage%2Fchange-password");
    }

    // POST: changed

    @Test
    void aValidChangeGoesToTheAreaHomeWithTheFlash() {
        when(identityClient.changePassword("account-2", SECRET, SECRET)).thenReturn(ChangePasswordResult.changed());

        MvcTestResult result = submit(SECRET, SECRET);

        assertThat(result).as("a successful POST answers 303").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("the area home is next").hasRedirectedUrl("/manage");
        assertThat(result).flash().as("the home page should show the banner").containsEntry(ManageController.PASSWORD_CHANGED, true);
        verify(identityClient, description("identity should get both values, for the signed-in account")).changePassword("account-2", SECRET, SECRET);
    }

    @Test
    void afterAValidChangeTheHomeOpensWithTheBannerOnceAndTheSessionIsPermanent() {
        when(identityClient.changePassword("account-2", SECRET, SECRET)).thenReturn(ChangePasswordResult.changed());
        MockHttpSession session = temporaryPasswordSession();
        String sessionId = session.getId();

        MvcTestResult changed = mvc.post().uri(PATH).session(session).with(csrf()).param("newPassword", SECRET).param("confirmNewPassword", SECRET).exchange();

        assertThat(changed).as("the change should succeed").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(((SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().getPrincipal())
                .as("the stored principal is no longer temporary").isEqualTo(new SessionPrincipal("account-2", AccountRole.PROPERTY_MANAGER, false));
        assertThat(changed.getRequest().getSession(false).getId()).as("this isn't a new sign-in").isEqualTo(sessionId);

        MvcTestResult first = mvc.get().uri("/manage").session(session).exchange();
        assertThat(first).as("the filter no longer redirects").hasStatusOk();
        assertThat(Html.page(first).select(".notification-banner--success").text()).as("the banner on the first visit").isEqualTo(BANNER);

        MvcTestResult second = mvc.get().uri("/manage").session(session).exchange();
        assertThat(second).as("the home page still opens").hasStatusOk();
        assertThat(Html.page(second).select(".notification-banner")).as("no banner on a reload").isEmpty();
        verify(identityClient, description("identity should have changed the password")).changePassword("account-2", SECRET, SECRET);
    }

    // POST: field errors

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "PASSWORD_REQUIRED | Enter a new password",
            "PASSWORD_TOO_SHORT | New password must be at least 8 characters",
            "PASSWORD_TOO_LONG | New password must be 128 characters or fewer",
            "PASSWORD_MISSING_CHARACTER_TYPES | New password must include an upper-case letter, a lower-case letter, a number and a symbol",
            "PASSWORD_SAME_AS_TEMPORARY | New password must be different from your temporary password" })
    void eachNewPasswordCodeShowsTheSpecsMessageInTheFieldAndTheSummary(String code, String message) {
        when(identityClient.changePassword("account-2", SECRET, OTHER_SECRET)).thenReturn(new ChangePasswordResult.FieldErrors(Map.of("newPassword", code)));

        MvcTestResult result = submit(SECRET, OTHER_SECRET);

        assertThat(result).as("validation errors re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title says there are errors").isEqualTo("Error: Change your password - Sunrise Letting");
        assertThat(page.select(".error-summary li a").eachText()).as("the summary has the message").containsExactly(message);
        assertThat(page.select(".error-summary li a").attr("href")).as("the summary links to the field").isEqualTo("#new-password");
        assertThat(page.select("#field-new-password.form-group--error #new-password-error-slot #new-password-error").text()).as("the field has the same message").isEqualTo("Error: " + message);
        assertThat(page.select("input#new-password").attr("aria-invalid")).as("the field is marked invalid").isEqualTo("true");
        assertThat(page.select("input#confirm-new-password").hasAttr("aria-invalid")).as("the other field isn't").isFalse();
        verify(identityClient, description("identity decides what is wrong")).changePassword("account-2", SECRET, OTHER_SECRET);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CONFIRM_PASSWORD_REQUIRED | Confirm your new password",
            "PASSWORDS_DO_NOT_MATCH | Passwords must match" })
    void eachConfirmCodeShowsTheSpecsMessageInTheFieldAndTheSummary(String code, String message) {
        when(identityClient.changePassword("account-2", SECRET, OTHER_SECRET)).thenReturn(new ChangePasswordResult.FieldErrors(Map.of("confirmNewPassword", code)));

        MvcTestResult result = submit(SECRET, OTHER_SECRET);

        assertThat(result).as("validation errors re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select(".error-summary li a").eachText()).as("the summary has the message").containsExactly(message);
        assertThat(page.select(".error-summary li a").attr("href")).as("the summary links to the field").isEqualTo("#confirm-new-password");
        assertThat(page.select("#field-confirm-new-password.form-group--error #confirm-new-password-error").text()).as("the field has the same message").isEqualTo("Error: " + message);
        assertThat(page.select("input#new-password").hasAttr("aria-invalid")).as("the other field isn't marked").isFalse();
        verify(identityClient, description("identity decides what is wrong")).changePassword("account-2", SECRET, OTHER_SECRET);
    }

    @Test
    void bothFieldsCanFailAndTheSummaryIsInFieldOrderBeforeTheHeading() {
        when(identityClient.changePassword("account-2", "", ""))
                .thenReturn(new ChangePasswordResult.FieldErrors(Map.of("confirmNewPassword", "CONFIRM_PASSWORD_REQUIRED", "newPassword", "PASSWORD_REQUIRED")));

        MvcTestResult result = submit("", "");

        Document page = Html.page(result);
        assertThat(page.select(".error-summary li a").eachAttr("href")).as("in the order of the fields").containsExactly("#new-password", "#confirm-new-password");
        assertThat(page.select(".error-summary + h1")).as("the summary comes straight before the heading").hasSize(1);
        assertThat(page.select(".error-summary[autofocus]")).as("the summary takes focus").hasSize(1);
        verify(identityClient, description("empty values go to identity as they are")).changePassword("account-2", "", "");
    }

    @Test
    void anAbsentFieldIsSentAsEmpty() {
        when(identityClient.changePassword("account-2", "", "")).thenReturn(new ChangePasswordResult.FieldErrors(Map.of("newPassword", "PASSWORD_REQUIRED")));

        MvcTestResult result = mvc.post().uri(PATH).with(csrf()).with(authentication(SignedInUsers.propertyManager(true))).exchange();

        assertThat(result).as("this is a validation error, not a bad request").hasStatusOk();
        verify(identityClient, description("a missing field counts as empty")).changePassword("account-2", "", "");
    }

    @Test
    void aFailedSubmitLeavesTheSessionTemporaryAndEmptiesBothFields() {
        when(identityClient.changePassword("account-2", SECRET, OTHER_SECRET)).thenReturn(new ChangePasswordResult.FieldErrors(Map.of("confirmNewPassword", "PASSWORDS_DO_NOT_MATCH")));
        MockHttpSession session = temporaryPasswordSession();

        MvcTestResult result = mvc.post().uri(PATH).session(session).with(csrf()).param("newPassword", SECRET).param("confirmNewPassword", OTHER_SECRET).exchange();

        assertThat(Html.body(result)).as("the new password is never rendered").doesNotContain(SECRET);
        assertThat(Html.body(result)).as("nor is the confirmation").doesNotContain(OTHER_SECRET);
        Document page = Html.page(result);
        assertThat(page.select("input#new-password").hasAttr("value")).as("the first field is empty").isFalse();
        assertThat(page.select("input#confirm-new-password").hasAttr("value")).as("the second field is empty").isFalse();
        assertThat(result).as("the page has no flash").flash().isEmpty();
        assertThat(mvc.get().uri("/manage").session(session).exchange()).as("the session still counts as temporary").hasRedirectedUrl(PATH);
        verify(identityClient, description("identity should have refused the change")).changePassword("account-2", SECRET, OTHER_SECRET);
    }

    // POST: unavailable

    @Test
    void unavailableGives503WithTheFormLevelMessageAndNoFlash() {
        when(identityClient.changePassword("account-2", SECRET, SECRET)).thenReturn(ChangePasswordResult.unavailable());
        MockHttpSession session = temporaryPasswordSession();

        MvcTestResult result = mvc.post().uri(PATH).session(session).with(csrf()).param("newPassword", SECRET).param("confirmNewPassword", SECRET).exchange();

        assertThat(result).as("a failing domain service gives 503").hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).as("nothing changed, so no banner").flash().isEmpty();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title says there are errors").isEqualTo("Error: Change your password - Sunrise Letting");
        assertThat(page.select(".error-summary .error-summary__body").text()).as("the form-level message").isEqualTo(UNAVAILABLE_MESSAGE);
        assertThat(page.select(".error-summary li")).as("no field is to blame").isEmpty();
        assertThat(page.select(".form-group--error")).as("no field is marked").isEmpty();
        assertThat(Html.body(result)).as("the password is never rendered").doesNotContain(SECRET);
        assertThat(mvc.get().uri("/manage").session(session).exchange()).as("the session still counts as temporary").hasRedirectedUrl(PATH);
        verify(identityClient, description("identity should have been asked")).changePassword("account-2", SECRET, SECRET);
    }

    // POST: the session's flag is out of date

    @Test
    void noTemporaryPasswordCorrectsTheSessionAndGoesToTheAreaHomeWithNoBanner() {
        when(identityClient.changePassword("account-2", SECRET, SECRET)).thenReturn(ChangePasswordResult.noTemporaryPassword());
        MockHttpSession session = temporaryPasswordSession();

        MvcTestResult result = mvc.post().uri(PATH).session(session).with(csrf()).param("newPassword", SECRET).param("confirmNewPassword", SECRET).exchange();

        assertThat(result).as("this POST leaves the page").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("the password is already permanent, so the area is open").hasRedirectedUrl("/manage");
        assertThat(result).as("this request changed nothing, so it can't say it did").flash().doesNotContainKey(ManageController.PASSWORD_CHANGED);
        MvcTestResult home = mvc.get().uri("/manage").session(session).exchange();
        assertThat(home).as("the person isn't stuck on the change-password page").hasStatusOk();
        assertThat(Html.page(home).select(".notification-banner")).as("no banner").isEmpty();
        verify(identityClient, description("identity should have been asked")).changePassword("account-2", SECRET, SECRET);
    }

    // POST: who can reach it

    @Test
    void aPermanentPasswordPostIsSentToTheAreaHomeWithoutCallingIdentity() {
        MvcTestResult result = submitAs(SignedInUsers.propertyManager(false), SECRET, SECRET);

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("to the area home").hasRedirectedUrl("/manage");
        assertThat(result).as("no banner").flash().isEmpty();
        verify(identityClient, never().description("nothing should be changed")).changePassword(any(), any(), any());
    }

    @Test
    void aVisitorPostIsDeniedWithoutCallingIdentity() {
        MvcTestResult result = submitAs(SignedInUsers.visitor(), SECRET, SECRET);

        assertThat(result).as("a Visitor is denied").hasStatus(HttpStatus.FORBIDDEN);
        verify(identityClient, never().description("nothing should be changed")).changePassword(any(), any(), any());
    }

    @Test
    void aPostWithNoCsrfTokenChangesNothing() {
        MvcTestResult result = mvc.post().uri(PATH).with(authentication(SignedInUsers.propertyManager(true))).param("newPassword", SECRET).param("confirmNewPassword", SECRET).exchange();

        assertThat(result).as("the request is refused").hasStatus(HttpStatus.FORBIDDEN);
        verify(identityClient, never().description("nothing should be changed")).changePassword(any(), any(), any());
    }

    @Test
    void aSignedOutPostGoesToSignInWithoutCallingIdentity() {
        MvcTestResult result = mvc.post().uri(PATH).with(csrf()).param("newPassword", SECRET).param("confirmNewPassword", SECRET).exchange();

        assertThat(result).as("the access rule comes first").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage%2Fchange-password");
        verify(identityClient, never().description("nothing should be changed")).changePassword(any(), any(), any());
    }

    private Document temporaryPasswordPage() {
        MvcTestResult result = mvc.get().uri(PATH).with(authentication(SignedInUsers.propertyManager(true))).exchange();
        assertThat(result).as("the page should load").hasStatusOk();
        return Html.page(result);
    }

    private MvcTestResult submit(String newPassword, String confirmNewPassword) {
        return submitAs(SignedInUsers.propertyManager(true), newPassword, confirmNewPassword);
    }

    private MvcTestResult submitAs(Authentication user, String newPassword, String confirmNewPassword) {
        return mvc.post().uri(PATH).with(csrf()).with(authentication(user)).param("newPassword", newPassword).param("confirmNewPassword", confirmNewPassword).exchange();
    }

    /** A session that holds the principal the way a real sign-in leaves it, so a change to it can be seen on later requests. */
    private MockHttpSession temporaryPasswordSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(SignedInUsers.propertyManager(true)));
        return session;
    }
}
