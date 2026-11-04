package com.sunriseletting.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.sunriseletting.web.identity.AccountField;
import com.sunriseletting.web.identity.FieldValidationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.testsupport.Html;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({ CreateAccountController.class, CreateAccountValidationController.class })
@Import({ SecurityConfiguration.class, IdentityErrorMessages.class, CreateAccountExpiredFormResponder.class,
        CreateAccountValidationExpiredFormResponder.class })
class CreateAccountValidationControllerTest {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String OTHER_SECRET = "0ther-Secret-Pa55!";

    private static final String TOO_LONG_FOR_OTHER_TITLE = "x".repeat(21);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IdentityClient identityClient;

    // One field, not the passwords

    @Test
    void invalidValueGivesOnlyTheGroupInItsErrorState() {
        when(identityClient.validateField(AccountField.FIRST_NAME, "much too long")).thenReturn(new FieldValidationResult.Invalid("FIRST_NAME_TOO_LONG"));

        MvcTestResult result = validate("first-name").param("firstName", "much too long").with(csrf()).exchange();

        assertThat(result).as("an invalid value should still be 200").hasStatusOk();
        assertThat(result).as("the fragment should be HTML").hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
        Document fragment = Html.fragment(result);
        assertThat(fragment.body().children()).as("the response should be only the group").hasSize(1);
        assertThat(fragment.select("html > head > *, main, header, footer")).as("the fragment should have no layout").isEmpty();
        Element group = fragment.body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-first-name");
        assertThat(group.hasClass("form-group--error")).as("the group should show the error state").isTrue();
        assertThat(group.select("label[for=first-name]").text()).as("the label should be there").isEqualTo("First name");
        assertThat(group.select("#first-name-error").text()).as("the spec's message should be there")
                .isEqualTo("Error: First name must be 100 characters or fewer");
        Element input = group.selectFirst("input#first-name");
        assertThat(input.attr("aria-invalid")).as("the input should be marked invalid").isEqualTo("true");
        assertThat(input.attr("value")).as("the value sent should be re-rendered").isEqualTo("much too long");
        assertThat(input.attr("hx-post")).as("the hx- attributes should be kept").isEqualTo("/create-account/validate/first-name");
        verify(identityClient, description("identity should check the value")).validateField(AccountField.FIRST_NAME, "much too long");
    }

    @Test
    void validValueGivesTheNeutralGroup() {
        when(identityClient.validateField(AccountField.SURNAME, " Bloggs ")).thenReturn(FieldValidationResult.valid());

        MvcTestResult result = validate("surname").param("surname", " Bloggs ").with(csrf()).exchange();

        assertThat(result).as("a valid value should be 200").hasStatusOk();
        Element group = Html.fragment(result).body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-surname");
        assertThat(group.hasClass("form-group--error")).as("no error state").isFalse();
        assertThat(group.select(".error-message")).as("no message").isEmpty();
        assertThat(group.selectFirst("input").hasAttr("aria-invalid")).as("no aria-invalid").isFalse();
        assertThat(group.selectFirst("input").attr("value")).as("the value should be kept as typed").isEqualTo(" Bloggs ");
        verify(identityClient, description("identity should check the value")).validateField(AccountField.SURNAME, " Bloggs ");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            title,       title,      TITLE,       TITLE_INVALID,            Select your title
            first-name,  firstName,  FIRST_NAME,  FIRST_NAME_TOO_LONG,      First name must be 100 characters or fewer
            surname,     surname,    SURNAME,     SURNAME_TOO_LONG,         Surname must be 100 characters or fewer
            email,       email,      EMAIL,       EMAIL_INVALID,            'Enter an email address in the correct format, like name@example.com'
            phone,       phone,      PHONE,       PHONE_INVALID_CHARACTERS, 'Phone number can only include numbers, spaces and a + at the start'
            first-name,  firstName,  FIRST_NAME,  FIRST_NAME_INVALID_CHARACTERS,  First name contains characters that can't be used
            surname,     surname,    SURNAME,     SURNAME_INVALID_CHARACTERS,     Surname contains characters that can't be used
            """)
    void eachFieldMapsToIdentitysNameAndTheMessage(String urlField, String parameter, AccountField field, String code, String message) {
        when(identityClient.validateField(field, "bad value")).thenReturn(new FieldValidationResult.Invalid(code));

        MvcTestResult result = validate(urlField).param(parameter, "bad value").with(csrf()).exchange();

        assertThat(result).as("an invalid value should still be 200").hasStatusOk();
        Element group = Html.fragment(result).body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-" + urlField);
        assertThat(group.select("#" + urlField + "-error").text()).as("the spec's message should be there").isEqualTo("Error: " + message);
        verify(identityClient, description("identity should be asked about its own field name")).validateField(field, "bad value");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            Other,   TITLE_OTHER_TOO_LONG,           Title must be 20 characters or fewer
            Other,   TITLE_OTHER_INVALID_CHARACTERS, Title contains characters that can't be used
            ' Other ', TITLE_OTHER_TOO_LONG,         Title must be 20 characters or fewer
            """)
    void otherTitleIsCheckedWhenTitleIsOther(String title, String code, String message) {
        when(identityClient.validateField(AccountField.TITLE_OTHER, "bad value")).thenReturn(new FieldValidationResult.Invalid(code));

        MvcTestResult result = validate("title-other").param("title", title).param("titleOther", "bad value").with(csrf()).exchange();

        assertThat(result).as("an invalid value should still be 200").hasStatusOk();
        Element group = Html.fragment(result).body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-title-other");
        assertThat(group.select("#title-other-error").text()).as("the spec's message should be there").isEqualTo("Error: " + message);
        verify(identityClient, description("identity should be asked about Other title")).validateField(AccountField.TITLE_OTHER, "bad value");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Mr", "other", "" })
    void otherTitleIsIgnoredWhenTitleIsNotOther(String title) {
        MvcTestResult result = validate("title-other").param("title", title).param("titleOther", TOO_LONG_FOR_OTHER_TITLE).with(csrf()).exchange();

        assertThat(result).as("an ignored value should be 200").hasStatusOk();
        Element group = Html.fragment(result).body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-title-other");
        assertThat(group.hasClass("form-group--error")).as("no error unless Title is Other").isFalse();
        assertThat(group.select(".error-message")).as("no message unless Title is Other").isEmpty();
        assertThat(group.selectFirst("input").attr("value")).as("what was typed should be kept").isEqualTo(TOO_LONG_FOR_OTHER_TITLE);
        assertIdentityNotCalled("Other title is ignored on submit unless Title is Other, so it shouldn't be checked here");
    }

    @Test
    void otherTitleIsIgnoredWhenNoTitleIsSent() {
        MvcTestResult result = validate("title-other").param("titleOther", TOO_LONG_FOR_OTHER_TITLE).with(csrf()).exchange();

        assertThat(result).as("an ignored value should be 200").hasStatusOk();
        assertThat(Html.fragment(result).select(".error-message")).as("no message unless Title is Other").isEmpty();
        assertIdentityNotCalled("no Title sent means it isn't Other, so Other title shouldn't be checked");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            title,       title
            title-other, titleOther
            first-name,  firstName
            surname,     surname
            email,       email
            phone,       phone
            """)
    void emptyValueGivesTheNeutralGroupWithoutCallingIdentity(String urlField, String parameter) {
        MvcTestResult result = validate(urlField).param(parameter, "   ").with(csrf()).exchange();

        assertThat(result).as("an empty value should be 200").hasStatusOk();
        Element group = Html.fragment(result).body().child(0);
        assertThat(group.id()).as("the group should be the swap target").isEqualTo("field-" + urlField);
        assertThat(group.hasClass("form-group--error")).as("no error until submit").isFalse();
        assertThat(group.select(".error-message")).as("no message until submit").isEmpty();
        verifyNoInteractions(identityClient);
    }

    @Test
    void missingValueCountsAsEmpty() {
        MvcTestResult result = validate("surname").with(csrf()).exchange();

        assertThat(result).as("a missing value should be 200").hasStatusOk();
        assertThat(Html.fragment(result).select(".form-group--error")).as("no error until submit").isEmpty();
        verifyNoInteractions(identityClient);
    }

    @Test
    void emailOfAnExistingAccountGivesTheNeutralGroup() {
        when(identityClient.validateField(AccountField.EMAIL, "taken@example.com")).thenReturn(FieldValidationResult.valid());

        MvcTestResult result = validate("email").param("email", "taken@example.com").with(csrf()).exchange();

        assertThat(result).as("the check should be 200").hasStatusOk();
        assertThat(Html.fragment(result).select(".form-group--error")).as("the inline check never reveals whether an account exists").isEmpty();
        verify(identityClient, description("identity only checks the format")).validateField(AccountField.EMAIL, "taken@example.com");
    }

    @Test
    void titleGroupKeepsItsSelectAndChosenOption() {
        when(identityClient.validateField(AccountField.TITLE, "Dr")).thenReturn(FieldValidationResult.valid());

        MvcTestResult result = validate("title").param("title", "Dr").with(csrf()).exchange();

        assertThat(result).as("the check should be 200").hasStatusOk();
        assertThat(Html.fragment(result).select("select#title option[selected]").eachText()).as("the chosen title should be kept").containsExactly("Dr");
        verify(identityClient, description("identity should check the value")).validateField(AccountField.TITLE, "Dr");
    }

    @Test
    void phoneGroupKeepsItsHintInDescribedBy() {
        when(identityClient.validateField(AccountField.PHONE, "123")).thenReturn(new FieldValidationResult.Invalid("PHONE_INVALID"));

        MvcTestResult result = validate("phone").param("phone", "123").with(csrf()).exchange();

        assertThat(result).as("the check should be 200").hasStatusOk();
        Document fragment = Html.fragment(result);
        assertThat(fragment.select("#phone-hint")).as("the hint should be in the fragment").hasSize(1);
        assertThat(fragment.selectFirst("#phone").attr("aria-describedby")).as("the input should reference the hint and the error")
                .isEqualTo("phone-hint phone-error");
        verify(identityClient, description("identity should check the value")).validateField(AccountField.PHONE, "123");
    }

    // Password and Confirm password

    @Test
    void invalidPasswordGivesOnlyTheErrorMessageAndNoPassword() {
        when(identityClient.validateField(AccountField.PASSWORD, SECRET)).thenReturn(new FieldValidationResult.Invalid("PASSWORD_MISSING_CHARACTER_TYPES"));

        MvcTestResult result = validate("password").param("password", SECRET).with(csrf()).exchange();

        assertThat(result).as("an invalid value should still be 200").hasStatusOk();
        Document fragment = Html.fragment(result);
        assertThat(fragment.body().children()).as("the response should be only the message").hasSize(1);
        Element message = fragment.body().child(0);
        assertThat(message.tagName()).as("the message is a paragraph").isEqualTo("p");
        assertThat(message.hasClass("error-message")).as("the message has the error class").isTrue();
        assertThat(message.id()).as("the message has the id aria-describedby lists").isEqualTo("password-error");
        assertThat(message.text()).as("the spec's message should be there")
                .isEqualTo("Error: Password must include an upper-case letter, a lower-case letter, a number and a symbol");
        assertThat(Html.body(result)).as("the response should never contain the password").doesNotContain(SECRET);
        verify(identityClient, description("identity should check the value")).validateField(AccountField.PASSWORD, SECRET);
    }

    @Test
    void validPasswordGivesAnEmptyBody() {
        when(identityClient.validateField(AccountField.PASSWORD, SECRET)).thenReturn(FieldValidationResult.valid());

        MvcTestResult result = validate("password").param("password", SECRET).with(csrf()).exchange();

        assertThat(result).as("a valid value should be 200").hasStatusOk();
        assertThat(Html.body(result)).as("a valid password should give an empty body").isBlank();
        verify(identityClient, description("identity should check the value")).validateField(AccountField.PASSWORD, SECRET);
    }

    @Test
    void emptyPasswordGivesAnEmptyBodyWithoutCallingIdentity() {
        MvcTestResult result = validate("password").param("password", "").with(csrf()).exchange();

        assertThat(result).as("an empty value should be 200").hasStatusOk();
        assertThat(Html.body(result)).as("an empty password should give an empty body").isBlank();
        verifyNoInteractions(identityClient);
    }

    @Test
    void confirmPasswordIsComparedWithThePasswordSentAndGivesOnlyTheMessage() {
        when(identityClient.validateField(AccountField.CONFIRM_PASSWORD, OTHER_SECRET, SECRET))
                .thenReturn(new FieldValidationResult.Invalid("PASSWORDS_DO_NOT_MATCH"));

        MvcTestResult result = validate("confirm-password").param("password", SECRET).param("confirmPassword", OTHER_SECRET).with(csrf()).exchange();

        assertThat(result).as("an invalid value should still be 200").hasStatusOk();
        Element message = Html.fragment(result).body().child(0);
        assertThat(message.id()).as("the message has the id aria-describedby lists").isEqualTo("confirm-password-error");
        assertThat(message.text()).as("the spec's message should be there").isEqualTo("Error: Passwords must match");
        assertThat(Html.body(result)).as("the response should never contain a password").doesNotContain(SECRET).doesNotContain(OTHER_SECRET);
        verify(identityClient, description("identity should compare with the password sent"))
                .validateField(AccountField.CONFIRM_PASSWORD, OTHER_SECRET, SECRET);
    }

    @Test
    void emptyConfirmPasswordGivesAnEmptyBodyWithoutCallingIdentity() {
        MvcTestResult result = validate("confirm-password").param("password", SECRET).param("confirmPassword", "").with(csrf()).exchange();

        assertThat(result).as("an empty value should be 200").hasStatusOk();
        assertThat(Html.body(result)).as("an empty value should give an empty body").isBlank();
        verifyNoInteractions(identityClient);
    }

    // Can't check, and requests that aren't from HTMX

    @Test
    void identityUnavailableGives204ForAPlainField() {
        when(identityClient.validateField(AccountField.EMAIL, "jo@example.com")).thenReturn(FieldValidationResult.unavailable());

        MvcTestResult result = validate("email").param("email", "jo@example.com").with(csrf()).exchange();

        assertThat(result).as("a check that can't be done changes nothing").hasStatus(HttpStatus.NO_CONTENT);
        assertThat(Html.body(result)).as("204 has no body").isEmpty();
        verify(identityClient, description("identity should be tried")).validateField(AccountField.EMAIL, "jo@example.com");
    }

    @Test
    void identityUnavailableGives204ForAPassword() {
        when(identityClient.validateField(AccountField.PASSWORD, SECRET)).thenReturn(FieldValidationResult.unavailable());

        MvcTestResult result = validate("password").param("password", SECRET).with(csrf()).exchange();

        assertThat(result).as("a check that can't be done changes nothing").hasStatus(HttpStatus.NO_CONTENT);
        verify(identityClient, description("identity should be tried")).validateField(AccountField.PASSWORD, SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = { "first-name", "password", "confirm-password" })
    void requestWithoutHxRequestRedirectsToTheForm(String urlField) {
        MvcTestResult result = mvc.post().uri("/create-account/validate/" + urlField).param("firstName", "Jo").with(csrf()).exchange();

        assertThat(result).as("these endpoints serve HTMX only").hasStatus(HttpStatus.SEE_OTHER);
        assertThat(result).as("a plain request should go to the form").hasRedirectedUrl("/create-account");
        verifyNoInteractions(identityClient);
    }

    @Test
    void unknownFieldIsNotFound() {
        MvcTestResult result = validate("nonsense").with(csrf()).exchange();

        assertThat(result).as("only the form's fields have an endpoint").hasStatus(HttpStatus.NOT_FOUND);
        verifyNoInteractions(identityClient);
    }

    // Expired form token

    @ParameterizedTest
    @CsvSource(textBlock = """
            first-name,       firstName
            password,         password
            confirm-password, confirmPassword
            """)
    void missingTokenGives204AndNotA403(String urlField, String parameter) {
        MvcTestResult result = validate(urlField).param(parameter, "value").exchange();

        assertThat(result).as("an expired token changes nothing, never a 403").hasStatus(HttpStatus.NO_CONTENT);
        assertThat(Html.body(result)).as("204 has no body").isEmpty();
        verifyNoInteractions(identityClient);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            first-name,       firstName
            password,         password
            confirm-password, confirmPassword
            """)
    void invalidTokenGives204AndLeavesTheSessionsTokenAlone(String urlField, String parameter) {
        MockHttpSession session = new MockHttpSession();
        MvcTestResult page = mvc.get().uri("/create-account").session(session).exchange();
        assertThat(page).as("opening the page should give the session a token").hasStatusOk();
        CsrfToken before = sessionToken(session);
        assertThat(before).as("the session should have a token").isNotNull();

        MvcTestResult result = validate(urlField).param(parameter, "value").param("_csrf", "not-the-token").session(session).exchange();

        assertThat(result).as("an expired token changes nothing, never a 403").hasStatus(HttpStatus.NO_CONTENT);
        assertThat(Html.body(result)).as("204 has no body").isEmpty();
        assertThat(sessionToken(session).getToken()).as("the session's CSRF token should be left as it is").isEqualTo(before.getToken());
        verifyNoInteractions(identityClient);
    }

    // The page turns inline validation on

    @ParameterizedTest
    @CsvSource(textBlock = """
            title,       title,      'title,_csrf'
            title-other, titleOther, 'title,titleOther,_csrf'
            first-name,  firstName,  'firstName,_csrf'
            surname,     surname,    'surname,_csrf'
            email,       email,      'email,_csrf'
            phone,       phone,      'phone,_csrf'
            """)
    void pageInputsCarryTheWholeGroupHxAttributes(String id, String name, String params) {
        Element input = createAccountPage().selectFirst("#" + id);

        assertThat(input.attr("hx-post")).as(id + " should post to its own endpoint").isEqualTo("/create-account/validate/" + id);
        assertThat(input.attr("hx-trigger")).as(id + " should be checked on blur").isEqualTo("blur");
        assertThat(input.attr("hx-target")).as(id + " should swap its whole group").isEqualTo("#field-" + id);
        assertThat(input.attr("hx-swap")).as(id + " should swap its whole group").isEqualTo("outerHTML");
        assertThat(input.attr("hx-params")).as(id + " should send only what its check needs and the token").isEqualTo(params);
    }

    @Test
    void passwordInputsCarryTheErrorSlotHxAttributes() {
        Document page = createAccountPage();

        Element password = page.selectFirst("#password");
        assertThat(password.attr("hx-post")).as("Password's endpoint").isEqualTo("/create-account/validate/password");
        assertThat(password.attr("hx-trigger")).as("Password is checked on blur").isEqualTo("blur");
        assertThat(password.attr("hx-target")).as("only Password's slot is swapped").isEqualTo("#password-error-slot");
        assertThat(password.attr("hx-swap")).as("only the slot's content is swapped").isEqualTo("innerHTML");
        assertThat(password.attr("hx-params")).as("Password sends only itself and the token").isEqualTo("password,_csrf");

        Element confirm = page.selectFirst("#confirm-password");
        assertThat(confirm.attr("hx-post")).as("Confirm password's endpoint").isEqualTo("/create-account/validate/confirm-password");
        assertThat(confirm.attr("hx-trigger")).as("Confirm password is checked on blur").isEqualTo("blur");
        assertThat(confirm.attr("hx-target")).as("only Confirm password's slot is swapped").isEqualTo("#confirm-password-error-slot");
        assertThat(confirm.attr("hx-swap")).as("only the slot's content is swapped").isEqualTo("innerHTML");
        assertThat(confirm.attr("hx-params")).as("Confirm password also sends Password").isEqualTo("password,confirmPassword,_csrf");
    }

    private MockMvcRequestBuilder validate(String urlField) {
        return mvc.post().uri("/create-account/validate/" + urlField).header("HX-Request", "true");
    }

    private Document createAccountPage() {
        MvcTestResult result = mvc.get().uri("/create-account").exchange();
        assertThat(result).as("the page should load").hasStatusOk();
        return Html.page(result);
    }

    /** Like verifyNoInteractions, which can't carry a message. */
    private void assertIdentityNotCalled(String because) {
        assertThat(mockingDetails(identityClient).getInvocations()).as(because).isEmpty();
    }

    private CsrfToken sessionToken(MockHttpSession session) {
        return (CsrfToken) session.getAttribute(HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN"));
    }
}
