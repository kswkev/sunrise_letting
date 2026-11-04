package com.sunriseletting.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.sunriseletting.web.identity.CreateVisitorRequest;
import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.RememberMeLogin;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.testsupport.Html;
import com.sunriseletting.web.testsupport.RecordingExchangeGate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.validation.BindingResult;

@WebMvcTest(CreateAccountController.class)
@Import({ SecurityConfiguration.class, IdentityErrorMessages.class, CreateAccountExpiredFormResponder.class, RememberMeLogin.class, RecordingExchangeGate.class })
class CreateAccountControllerTest {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String OTHER_SECRET = "0ther-Secret-Pa55!";

    /** What identity should get for {@link #validForm()}. */
    private static final CreateVisitorRequest VALID_REQUEST =
            new CreateVisitorRequest("Mr", "", "Jo", "Bloggs", "jo@example.com", "07700 900982", SECRET, SECRET);

    private static final CreateVisitorResult CREATED =
            new CreateVisitorResult.Created("8f14e45f-ceea-467f-a8b2-1d2f6a7b9c10", "VISITOR");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdentityClient identityClient;

    // GET: the page

    @Test
    void pageHasTheTitleHeadingAndIntro() {
        Document page = getPage("/create-account");

        assertThat(page.title()).as("the title should name the page").isEqualTo("Create an account - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("the heading should name the page").isEqualTo("Create an account");
        assertThat(page.select("main").text()).as("the intro should say why")
                .contains("You need an account to request a viewing or apply for a property.");
        assertThat(page.select(".error-summary")).as("no error summary on first load").isEmpty();
        assertThat(page.select("header nav a[aria-current=page]").eachText()).as("Create account should be current")
                .containsExactly("Create account");
    }

    @Test
    void formIsAPostWithNovalidateAndACsrfField() {
        Element form = getPage("/create-account").selectFirst("form#create-account-form");

        assertThat(form.attr("method")).as("the form should POST").isEqualToIgnoringCase("post");
        assertThat(form.attr("action")).as("the form should post to the page").isEqualTo("/create-account");
        assertThat(form.hasAttr("novalidate")).as("the server reports errors, not the browser").isTrue();
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the form should carry a CSRF token").isNotBlank();
        assertThat(form.select("button[type=submit].button").text()).as("the submit button has the agreed text").isEqualTo("Create account");
    }

    @Test
    void fieldsAreInTheSpecsOrderWithTheirAttributes() {
        Element form = getPage("/create-account").selectFirst("form#create-account-form");

        assertThat(form.select(".form-group").eachAttr("id")).as("fields should be in the spec's order").containsExactly(
                "field-title", "field-title-other", "field-first-name", "field-surname", "field-email", "field-phone",
                "field-password", "field-confirm-password");
        assertThat(form.select(".form-group label").eachText()).as("labels should be the spec's").containsExactly(
                "Title", "Other title", "First name", "Surname", "Email address", "Phone number", "Password", "Confirm password");
        assertThat(form.select(".form-group input, .form-group select").eachAttr("name")).as("names should be the spec's").containsExactly(
                "title", "titleOther", "firstName", "surname", "email", "phone", "password", "confirmPassword");

        Element title = form.selectFirst("select#title");
        assertThat(title.select("option").eachText()).as("Title should list the agreed options")
                .containsExactly("Select a title", "Mr", "Mrs", "Miss", "Ms", "Mx", "Dr", "Other");
        assertThat(title.select("option").first().attr("value")).as("Title should start with an empty value").isEmpty();
        assertThat(title.select("option[selected]")).as("no title should be chosen for the user").isEmpty();
        assertThat(title.attr("autocomplete")).as("Title autocomplete").isEqualTo("honorific-prefix");

        assertThat(form.selectFirst("#title-other").attr("autocomplete")).as("Other title autocomplete").isEqualTo("off");
        assertThat(form.selectFirst("#title-other").attr("spellcheck")).as("Other title spellcheck").isEqualTo("false");
        assertThat(form.select("#title-other-hint").text()).as("Other title hint").isEqualTo("Only if you chose Other");
        assertThat(form.selectFirst("#first-name").attr("autocomplete")).as("First name autocomplete").isEqualTo("given-name");
        assertThat(form.selectFirst("#first-name").attr("spellcheck")).as("First name spellcheck").isEqualTo("false");
        assertThat(form.selectFirst("#surname").attr("autocomplete")).as("Surname autocomplete").isEqualTo("family-name");
        assertThat(form.selectFirst("#surname").attr("spellcheck")).as("Surname spellcheck").isEqualTo("false");
        assertThat(form.selectFirst("#email").attr("type")).as("Email type").isEqualTo("email");
        assertThat(form.selectFirst("#email").attr("autocomplete")).as("Email autocomplete").isEqualTo("email");
        assertThat(form.selectFirst("#email").attr("spellcheck")).as("Email spellcheck").isEqualTo("false");
        assertThat(form.selectFirst("#email").attr("autocapitalize")).as("Email autocapitalize").isEqualTo("none");
        assertThat(form.selectFirst("#phone").attr("type")).as("Phone type").isEqualTo("tel");
        assertThat(form.selectFirst("#phone").attr("autocomplete")).as("Phone autocomplete").isEqualTo("tel");
        assertThat(form.select("#password-hint").text()).as("Password hint").isEqualTo(
                "Must be 8 to 128 characters and include an upper-case letter, a lower-case letter, a number and a symbol");
        assertThat(form.selectFirst("#password").attr("autocomplete")).as("Password autocomplete").isEqualTo("new-password");
        assertThat(form.selectFirst("#confirm-password").attr("autocomplete")).as("Confirm password autocomplete").isEqualTo("new-password");
        assertThat(form.select(".password-field__toggle[aria-controls=password]").text()).as("Password toggle name").isEqualTo("Show password");
        assertThat(form.select(".password-field__toggle[aria-controls=confirm-password]").text()).as("Confirm toggle name").isEqualTo("Show confirm password");
    }

    @Test
    void noFieldHasRequiredOrMaxlengthAndThePasswordScriptIsLoaded() {
        Document page = getPage("/create-account");

        assertThat(page.select("[required], [maxlength]")).as("the server reports these errors itself").isEmpty();
        Element script = page.selectFirst("head script[src=/js/password-toggle.js]");
        assertThat(script).as("the page should load the password field script").isNotNull();
        assertThat(script.hasAttr("defer")).as("the script should be deferred").isTrue();
        assertThat(page.select("script:not([src])")).as("no inline script").isEmpty();
    }

    @Test
    void phoneHintHasTheSpecsTextAndDescribesTheInput() {
        Document page = getPage("/create-account");

        assertThat(page.select("p.hint#phone-hint").text()).as("the hint should say what is accepted")
                .isEqualTo("Use numbers and spaces only, and a + at the start if you need it, for example 07700 900982");
        assertThat(page.selectFirst("#phone").attr("aria-describedby")).as("the input should reference the hint").isEqualTo("phone-hint");
    }

    @Test
    void validReturnToIsKeptInAHiddenFieldAndOnTheSignInLink() {
        Document page = getPage("/create-account?returnTo=/properties/123");

        assertThat(page.select("form#create-account-form input[type=hidden][name=returnTo]").attr("value"))
                .as("a valid return address should be in the form").isEqualTo("/properties/123");
        assertThat(page.select("main a:contains(Sign in)").attr("href")).as("the sign-in link should carry it")
                .isEqualTo("/sign-in?returnTo=/properties/123");
    }

    @Test
    void headerLinksCarryNoReturnAddressOnThisPage() {
        Document page = getPage("/create-account?returnTo=/properties/123");

        assertThat(page.select("header nav a").eachAttr("href")).as("header links carry no return address")
                .containsExactly("/properties", "/sign-in", "/create-account");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)" })
    void unsafeReturnToIsLeftOut(String returnTo) {
        MvcTestResult result = mvc.get().uri("/create-account").param("returnTo", returnTo).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("input[name=returnTo]")).as("an unsafe return address shouldn't be in the form").isEmpty();
        assertThat(page.select("main a:contains(Sign in)").attr("href")).as("the sign-in link shouldn't carry it").isEqualTo("/sign-in");
    }

    // Already signed in

    @Test
    void getWhenSignedInRedirectsToPropertiesWithNoForm() {
        MvcTestResult result = mvc.get().uri("/create-account?returnTo=/properties/123").with(authentication(visitor())).exchange();

        assertThat(result).as("a signed-in Visitor should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("any return address should be ignored").hasRedirectedUrl("/properties");
        assertThat(Html.body(result)).as("no form should be shown").doesNotContain("create-account-form");
    }

    @Test
    void postWhenSignedInRedirectsToPropertiesAndCreatesNothing() {
        MvcTestResult result = submit(validForm()).with(authentication(visitor())).with(csrf()).exchange();

        assertThat(result).as("a signed-in Visitor should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("a signed-in Visitor goes to the search page").hasRedirectedUrl("/properties");
        assertIdentityNotCalled("a signed-in Visitor shouldn't create an account");
    }

    @Test
    void postWithoutACsrfTokenWhenSignedInStillRedirects() {
        MvcTestResult result = submit(validForm()).with(authentication(visitor())).exchange();

        assertThat(result).as("a signed-in Visitor should be redirected, not shown the form").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("a signed-in Visitor goes to the search page").hasRedirectedUrl("/properties");
        assertIdentityNotCalled("a signed-in Visitor with no token shouldn't create an account");
    }

    // POST: created

    @Test
    void signingUpOverAnEarlierLoginsCookieClearsItAndRevokesItsSeries() {
        String series = "A".repeat(22);
        when(identityClient.createVisitor(any())).thenReturn(CREATED);

        MvcTestResult result = submit(validForm()).cookie(new Cookie("remember-me", series + "." + "B".repeat(43))).with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).as("the cookie would otherwise sign the browser in as the earlier account")
                .anyMatch(header -> header.startsWith("remember-me=;") && header.contains("Max-Age=0"));
        verify(identityClient, description("the earlier login should be revoked")).revokeRememberMeToken(series);
        verify(identityClient, description("identity should create the account")).createVisitor(VALID_REQUEST);
    }

    @Test
    void signingUpWithNoCookieRevokesNothing() {
        when(identityClient.createVisitor(any())).thenReturn(CREATED);

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result.getResponse().getHeaders("Set-Cookie")).as("there's nothing to clear").noneMatch(header -> header.startsWith("remember-me="));
        verify(identityClient, never().description("there's no series to revoke")).revokeRememberMeToken(any());
        verify(identityClient, description("identity should create the account")).createVisitor(VALID_REQUEST);
    }

    @Test
    void validDetailsWithAReturnAddressSignInAndGoBackToIt() {
        when(identityClient.createVisitor(any())).thenReturn(CREATED);

        MvcTestResult result = submit(validForm()).param("returnTo", "/properties/123").with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("the Visitor should go back to the page they came from").hasRedirectedUrl("/properties/123");
        assertThat(result).flash().as("there should be no banner on the return page").doesNotContainKey(PropertiesController.ACCOUNT_CREATED);
        assertSignedInAsVisitor(result);
        verify(identityClient, description("identity should get every field exactly as typed")).createVisitor(VALID_REQUEST);
    }

    @Test
    void validDetailsWithNoReturnAddressGoToPropertiesWithTheBanner() {
        when(identityClient.createVisitor(any())).thenReturn(CREATED);

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("with no return address the Visitor goes to the search page").hasRedirectedUrl("/properties");
        assertThat(result).flash().as("the search page should show the banner").containsEntry(PropertiesController.ACCOUNT_CREATED, true);
        assertSignedInAsVisitor(result);
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)" })
    void unsafeReturnAddressGoesToPropertiesWithTheBanner(String returnTo) {
        when(identityClient.createVisitor(any())).thenReturn(CREATED);

        MvcTestResult result = submit(validForm()).param("returnTo", returnTo).with(csrf()).exchange();

        assertThat(result).as("success should answer See Other").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("an unsafe return address counts as none").hasRedirectedUrl("/properties");
        assertThat(result).flash().as("the search page should show the banner").containsEntry(PropertiesController.ACCOUNT_CREATED, true);
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    // POST: validation failed

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            title           | TITLE_REQUIRED                   | title            | Select your title
            title           | TITLE_INVALID                    | title            | Select your title
            titleOther      | TITLE_OTHER_REQUIRED             | title-other      | Enter your title
            titleOther      | TITLE_OTHER_TOO_LONG             | title-other      | Title must be 20 characters or fewer
            titleOther      | TITLE_OTHER_INVALID_CHARACTERS   | title-other      | Title contains characters that can't be used
            firstName       | FIRST_NAME_REQUIRED              | first-name       | Enter your first name
            firstName       | FIRST_NAME_TOO_LONG              | first-name       | First name must be 100 characters or fewer
            firstName       | FIRST_NAME_INVALID_CHARACTERS    | first-name       | First name contains characters that can't be used
            surname         | SURNAME_REQUIRED                 | surname          | Enter your surname
            surname         | SURNAME_TOO_LONG                 | surname          | Surname must be 100 characters or fewer
            surname         | SURNAME_INVALID_CHARACTERS       | surname          | Surname contains characters that can't be used
            email           | EMAIL_REQUIRED                   | email            | Enter your email address
            email           | EMAIL_TOO_LONG                   | email            | Email address must be 254 characters or fewer
            email           | EMAIL_INVALID                    | email            | Enter an email address in the correct format, like name@example.com
            phone           | PHONE_REQUIRED                   | phone            | Enter your phone number
            phone           | PHONE_INVALID_CHARACTERS         | phone            | Phone number can only include numbers, spaces and a + at the start
            phone           | PHONE_INVALID                    | phone            | Enter a UK phone number, like 07700 900982 or +44 7700 900982
            password        | PASSWORD_REQUIRED                | password         | Enter a password
            password        | PASSWORD_TOO_SHORT               | password         | Password must be at least 8 characters
            password        | PASSWORD_TOO_LONG                | password         | Password must be 128 characters or fewer
            password        | PASSWORD_MISSING_CHARACTER_TYPES | password         | Password must include an upper-case letter, a lower-case letter, a number and a symbol
            confirmPassword | CONFIRM_PASSWORD_REQUIRED        | confirm-password | Confirm your password
            confirmPassword | PASSWORDS_DO_NOT_MATCH           | confirm-password | Passwords must match
            """)
    void eachCodeShowsTheSpecsMessageInTheFieldAndTheSummary(String field, String code, String inputId, String message) {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of(field, code)));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result).as("validation errors should re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should have the error prefix").isEqualTo("Error: Create an account - Sunrise Letting");
        assertThat(page.select("#" + inputId + "-error").text()).as("the field should show the message").isEqualTo("Error: " + message);
        assertThat(page.select("#field-" + inputId).hasClass("form-group--error")).as("the field should be in its error state").isTrue();
        assertThat(page.selectFirst("#" + inputId).attr("aria-invalid")).as("the input should be marked invalid").isEqualTo("true");
        assertThat(page.select(".error-summary ul li a").eachText()).as("the summary should use the same message").containsExactly(message);
        assertThat(page.select(".error-summary ul li a").eachAttr("href")).as("the summary should link to the input").containsExactly("#" + inputId);
        assertNotSignedIn(result);
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    @Test
    void summaryListsTheFieldsInFormOrderAtTheTopOfMain() {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("confirmPassword", "PASSWORDS_DO_NOT_MATCH");
        errors.put("phone", "PHONE_REQUIRED");
        errors.put("firstName", "FIRST_NAME_REQUIRED");
        errors.put("title", "TITLE_REQUIRED");
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(errors));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result).as("validation errors should re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select(".error-summary ul li a").eachAttr("href")).as("the summary should be in field order")
                .containsExactly("#title", "#first-name", "#phone", "#confirm-password");
        Element summary = page.selectFirst("main .error-summary");
        assertThat(summary.hasAttr("autofocus")).as("the summary should take focus").isTrue();
        assertThat(summary.nextElementSibling().tagName()).as("the summary should come before the heading").isEqualTo("h1");
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    @Test
    void valuesComeBackAsTypedAndPasswordsDoNot() {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of("email", "EMAIL_INVALID")));

        MvcTestResult result = mvc.post().uri("/create-account").with(csrf())
                .param("title", "Other").param("titleOther", " Prof <b> ").param("firstName", "  Jo  ").param("surname", "O'Neil ")
                .param("email", " Not An Email ").param("phone", " 07700-900982 ")
                .param("password", SECRET).param("confirmPassword", OTHER_SECRET).exchange();

        assertThat(result).as("validation errors should re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("#title option[selected]").eachAttr("value")).as("the chosen title should be kept").containsExactly("Other");
        assertThat(page.selectFirst("#title-other").attr("value")).as("Other title as typed").isEqualTo(" Prof <b> ");
        assertThat(page.selectFirst("#first-name").attr("value")).as("First name as typed").isEqualTo("  Jo  ");
        assertThat(page.selectFirst("#surname").attr("value")).as("Surname as typed").isEqualTo("O'Neil ");
        assertThat(page.selectFirst("#email").attr("value")).as("Email as typed").isEqualTo(" Not An Email ");
        assertThat(page.selectFirst("#phone").attr("value")).as("Phone as typed").isEqualTo(" 07700-900982 ");
        assertThat(page.selectFirst("#password").hasAttr("value")).as("Password should never be rendered back").isFalse();
        assertThat(page.selectFirst("#confirm-password").hasAttr("value")).as("Confirm password should never be rendered back").isFalse();
        assertThat(Html.body(result)).as("the response shouldn't contain the password").doesNotContain(SECRET).doesNotContain(OTHER_SECRET);
        verify(identityClient, description("identity should check every field exactly as typed")).createVisitor(
                new CreateVisitorRequest("Other", " Prof <b> ", "  Jo  ", "O'Neil ", " Not An Email ", " 07700-900982 ", SECRET, OTHER_SECRET));
    }

    @Test
    void phoneErrorKeepsTheHintAndAddsTheErrorToDescribedBy() {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of("phone", "PHONE_INVALID")));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result).as("validation errors should re-render with 200").hasStatusOk();
        assertThat(Html.page(result).selectFirst("#phone").attr("aria-describedby")).as("the input should reference the hint and the error")
                .isEqualTo("phone-hint phone-error");
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    @Test
    void validReturnToSurvivesAFailedSubmit() {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of("email", "EMAIL_INVALID")));

        MvcTestResult result = submit(validForm()).param("returnTo", "/properties/123").with(csrf()).exchange();

        assertThat(result).as("validation errors should re-render with 200").hasStatusOk();
        assertThat(Html.page(result).select("input[type=hidden][name=returnTo]").attr("value")).as("the return address should be kept")
                .isEqualTo("/properties/123");
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    // POST: not created (duplicate)

    @Test
    void duplicateEmailShowsTheGenericFormLevelMessageWithASignInLink() {
        when(identityClient.createVisitor(any())).thenReturn(CreateVisitorResult.notCreated());

        MvcTestResult result = submit(validForm()).param("returnTo", "/properties/123").with(csrf()).exchange();

        assertThat(result).as("a duplicate should re-render with 200").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should have the error prefix").isEqualTo("Error: Create an account - Sunrise Letting");
        assertThat(page.select(".error-summary p.error-summary__body").text()).as("the message shouldn't confirm the email is registered")
                .isEqualTo("We couldn't create an account with these details. If you already have an account, sign in.");
        assertThat(page.select(".error-summary__body a").attr("href")).as("sign in should keep the return address")
                .isEqualTo("/sign-in?returnTo=/properties/123");
        assertThat(page.select(".form-group--error")).as("no field should be marked as wrong").isEmpty();
        assertThat(page.selectFirst("#email").attr("value")).as("values should be kept").isEqualTo("jo@example.com");
        assertThat(page.selectFirst("#password").hasAttr("value")).as("passwords shouldn't be kept").isFalse();
        assertThat(Html.body(result)).as("the response shouldn't contain the password").doesNotContain(SECRET);
        assertNotSignedIn(result);
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    @Test
    void duplicateSignInLinkHasNoReturnAddressWithoutAValidOne() {
        when(identityClient.createVisitor(any())).thenReturn(CreateVisitorResult.notCreated());

        MvcTestResult result = submit(validForm()).param("returnTo", "//evil.example").with(csrf()).exchange();

        assertThat(result).as("a duplicate should re-render with 200").hasStatusOk();
        assertThat(Html.page(result).select(".error-summary__body a").attr("href")).as("sign in shouldn't carry an unsafe address").isEqualTo("/sign-in");
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    // POST: unavailable

    @Test
    void identityUnavailableShowsTheServerErrorWith503() {
        when(identityClient.createVisitor(any())).thenReturn(CreateVisitorResult.unavailable());

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result).as("a failing identity should give 503").hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should have the error prefix").isEqualTo("Error: Create an account - Sunrise Letting");
        assertThat(page.select(".error-summary p.error-summary__body").text()).as("the message should be the server error")
                .isEqualTo("Sorry, we couldn't create your account. Try again later.");
        assertThat(page.select(".form-group--error")).as("no field should be marked as wrong").isEmpty();
        assertThat(page.selectFirst("#first-name").attr("value")).as("values should be kept").isEqualTo("Jo");
        assertThat(Html.body(result)).as("the response shouldn't contain the password").doesNotContain(SECRET);
        assertNotSignedIn(result);
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    // POST: a response outside the contract

    @Test
    void unknownErrorCodeShowsTheServerErrorWith503() {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of("email", "SOMETHING_NEW")));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertServerError(result);
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    @Test
    void unknownErrorCodeAmongKnownOnesShowsOnlyTheServerError() {
        when(identityClient.createVisitor(any())).thenReturn(
                new CreateVisitorResult.ValidationFailed(Map.of("email", "SOMETHING_NEW", "phone", "PHONE_REQUIRED")));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertServerError(result);
        verify(identityClient, description("identity should check the form")).createVisitor(VALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SOMETHING_NEW", "visitor", "", "PROPERTY_MANAGER" })
    void roleOtherThanVisitorShowsTheServerErrorAndSignsNobodyIn(String role) {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", role));

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertServerError(result);
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("everySubmitOutcome")
    void passwordsAreNotInTheModel(CreateVisitorResult outcome) {
        when(identityClient.createVisitor(any())).thenReturn(outcome);

        MvcTestResult result = submit(validForm()).with(csrf()).exchange();

        assertThat(result.getMvcResult().getModelAndView().getModel().values())
                .as("no form in the model, or held by a BindingResult there, should have a password")
                .map(value -> value instanceof BindingResult binding ? binding.getTarget() : value)
                .filteredOn(CreateAccountForm.class::isInstance).map(CreateAccountForm.class::cast)
                .allMatch(form -> form.password().isEmpty() && form.confirmPassword().isEmpty());
        verify(identityClient, description("identity should be asked to create the account")).createVisitor(VALID_REQUEST);
    }

    private static Stream<CreateVisitorResult> everySubmitOutcome() {
        return Stream.of(CREATED, new CreateVisitorResult.ValidationFailed(Map.of("email", "EMAIL_INVALID")), CreateVisitorResult.notCreated(),
                CreateVisitorResult.unavailable());
    }

    private void assertServerError(MvcTestResult result) {
        assertThat(result).as("a response outside the contract should give 503").hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        Document page = Html.page(result);
        assertThat(page.select(".error-summary p.error-summary__body").text()).as("the message should be the server error")
                .isEqualTo("Sorry, we couldn't create your account. Try again later.");
        assertThat(page.select(".error-summary ul")).as("the form-level summary has no list").isEmpty();
        assertThat(page.select(".form-group--error")).as("no field should be marked as wrong").isEmpty();
        assertThat(page.selectFirst("#first-name").attr("value")).as("values should be kept").isEqualTo("Jo");
        assertThat(Html.body(result)).as("the response shouldn't contain the password").doesNotContain(SECRET);
        assertNotSignedIn(result);
    }

    // POST: form expired

    @Test
    void missingCsrfTokenReshowsTheFormWithEverythingButThePasswords() {
        assertFormExpired(submit(filledForm()).param("returnTo", "/properties/123").exchange(), null);
    }

    @Test
    void invalidCsrfTokenReshowsTheFormWithEverythingButThePasswords() {
        assertFormExpired(submit(filledForm()).param("returnTo", "/properties/123").with(csrf().useInvalidToken()).exchange(), "invalid");
    }

    private void assertFormExpired(MvcTestResult result, String submittedToken) {
        assertThat(result).as("an expired form should re-render with 200, not 403").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should have the error prefix").isEqualTo("Error: Create an account - Sunrise Letting");
        assertThat(page.select(".error-summary p.error-summary__body").text()).as("the message should say the form expired")
                .isEqualTo("Your form has expired. Check your details and submit again.");
        assertThat(page.select(".error-summary ul")).as("the form-level summary has no list").isEmpty();
        assertThat(page.select(".form-group--error")).as("no field should be marked as wrong").isEmpty();

        assertThat(page.select("#title option[selected]").eachAttr("value")).as("Title should be kept").containsExactly("Other");
        assertThat(page.selectFirst("#title-other").attr("value")).as("Other title should be kept").isEqualTo(" Prof ");
        assertThat(page.selectFirst("#first-name").attr("value")).as("First name should be kept").isEqualTo("Jo");
        assertThat(page.selectFirst("#surname").attr("value")).as("Surname should be kept").isEqualTo("Bloggs");
        assertThat(page.selectFirst("#email").attr("value")).as("Email should be kept").isEqualTo("jo@example.com");
        assertThat(page.selectFirst("#phone").attr("value")).as("Phone should be kept").isEqualTo("07700 900982");
        assertThat(page.select("input[type=hidden][name=returnTo]").attr("value")).as("the return address should be kept").isEqualTo("/properties/123");

        assertThat(page.selectFirst("#password").hasAttr("value")).as("Password should never be rendered back").isFalse();
        assertThat(page.selectFirst("#confirm-password").hasAttr("value")).as("Confirm password should never be rendered back").isFalse();
        assertThat(Html.body(result)).as("the response shouldn't contain the passwords").doesNotContain(SECRET).doesNotContain(OTHER_SECRET);

        String token = page.select("form#create-account-form input[type=hidden][name=_csrf]").attr("value");
        assertThat(token).as("the re-shown form should have a CSRF token").isNotBlank().isNotEqualTo(submittedToken);

        assertThat(page.select("script[src=/js/password-toggle.js]")).as("the re-shown form should still load the password script").hasSize(1);
        assertIdentityNotCalled("an expired form shouldn't reach identity");
        assertNotSignedIn(result);
    }

    @Test
    void expiredFormDropsAnUnsafeReturnAddress() {
        MvcTestResult result = submit(filledForm()).param("returnTo", "//evil.example").exchange();

        assertThat(result).as("an expired form should re-render with 200").hasStatusOk();
        assertThat(Html.page(result).select("input[name=returnTo]")).as("an unsafe return address shouldn't be kept").isEmpty();
    }

    @Test
    void otherPostsWithoutACsrfTokenAreStillRejected() {
        MvcTestResult result = mvc.post().uri("/create-account/anything").exchange();

        assertThat(result).as("only registered forms get the expired form handling").hasStatus(HttpStatus.FORBIDDEN);
        verify(identityClient, never().description("nothing should reach identity")).createVisitor(any());
    }

    private MockMvcRequestBuilder submit(Map<String, String> form) {
        MockMvcRequestBuilder request = mvc.post().uri("/create-account");
        form.forEach(request::param);
        return request;
    }

    private Map<String, String> validForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("title", "Mr");
        form.put("titleOther", "");
        form.put("firstName", "Jo");
        form.put("surname", "Bloggs");
        form.put("email", "jo@example.com");
        form.put("phone", "07700 900982");
        form.put("password", SECRET);
        form.put("confirmPassword", SECRET);
        return form;
    }

    private Map<String, String> filledForm() {
        Map<String, String> form = validForm();
        form.put("title", "Other");
        form.put("titleOther", " Prof ");
        form.put("confirmPassword", OTHER_SECRET);
        return form;
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

    /** Like verifyNoInteractions, which can't carry a message. */
    private void assertIdentityNotCalled(String because) {
        assertThat(mockingDetails(identityClient).getInvocations()).as(because).isEmpty();
    }

    private void assertSignedInAsVisitor(MvcTestResult result) {
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).as("there should be a session").isNotNull();
        assertThat(session.getMaxInactiveInterval()).as("a Visitor's session should idle out after 30 minutes").isEqualTo(1800);
        SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).as("the session should be signed in").isNotNull();
        assertThat(context.getAuthentication().getPrincipal()).as("the principal should hold the account and the role identity returned")
                .isEqualTo(new SessionPrincipal("8f14e45f-ceea-467f-a8b2-1d2f6a7b9c10", AccountRole.VISITOR));
        assertThat(context.getAuthentication().getAuthorities()).as("the Visitor role should be the only role")
                .extracting(Object::toString).containsExactly("ROLE_VISITOR");
    }

    private void assertNotSignedIn(MvcTestResult result) {
        HttpSession session = result.getRequest().getSession(false);
        Object context = session == null ? null : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).as("no one should be signed in").isNull();
    }
}
