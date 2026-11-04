package com.sunriseletting.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.sunriseletting.web.identity.AccountField;
import com.sunriseletting.web.identity.CreateVisitorRequest;
import com.sunriseletting.web.identity.CreateVisitorResult;
import com.sunriseletting.web.identity.FieldValidationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.testsupport.Browser;
import com.sunriseletting.web.testsupport.BrowserTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The sign-up form in real Chrome, with identity mocked. This also settles the spec's two checks
 * against the htmx version in the WebJar: that hx-params limits what is sent, and that focus stays
 * in a swapped input with the same id.
 */
@BrowserTest
class CreateAccountInlineValidationIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    private static final String TOO_LONG_FOR_OTHER_TITLE = "x".repeat(21);

    private static final long CHECK_TIMEOUT_MILLIS = 5000;

    @MockitoBean
    private IdentityClient identityClient;

    @BeforeEach
    void resetMock() {
        reset(identityClient);
    }

    /** Records the body of each HTMX request as it goes on the wire, and counts the ones that have finished. */
    private void recordRequests(Browser browser) {
        browser.script("""
                window.sentBodies = [];
                const send = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function (body) {
                    window.sentBodies.push(String(body));
                    return send.call(this, body);
                };
                document.body.addEventListener('htmx:afterRequest', () => {
                    document.body.dataset.requests = String(Number(document.body.dataset.requests || 0) + 1);
                });
                """);
    }

    private List<String> parametersOfLastRequest(Browser browser) {
        return browser.script("return [...new URLSearchParams(window.sentBodies.at(-1)).keys()].sort()");
    }

    private void waitForRequests(Browser browser, int count) {
        browser.waitUntil(count + " HTMX request(s) to finish",
                () -> String.valueOf(count).equals(browser.find("body").getDomAttribute("data-requests")));
    }

    @Test
    void leavingAFieldSwapsOnlyThatFieldAndSendsOnlyItsValueAndTheToken(Browser browser) {
        when(identityClient.validateField(any(), anyString())).thenReturn(FieldValidationResult.valid());
        when(identityClient.validateField(AccountField.EMAIL, "not-an-email")).thenReturn(new FieldValidationResult.Invalid("EMAIL_INVALID"));
        browser.open("/create-account");
        recordRequests(browser);
        browser.find("#first-name").sendKeys("Jo");
        browser.find("#password").sendKeys(SECRET);
        browser.script("document.querySelector('#surname').dataset.untouched = 'yes'");

        browser.find("#email").sendKeys("not-an-email");
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the email error to be swapped in", () -> !browser.driver().findElements(By.id("email-error")).isEmpty());
        // First name's own check can still be on its way, and would swap the input out while the test reads it.
        waitForRequests(browser, 3);

        assertEquals("Error: Enter an email address in the correct format, like name@example.com", browser.find("#email-error").getText().replaceAll("\\s+", " "),
                "the field should show the message");
        assertTrue(browser.find("#field-email").getDomAttribute("class").contains("form-group--error"), "the field should show the error state");
        assertEquals("true", browser.find("#email").getDomAttribute("aria-invalid"), "the input should be marked invalid");
        assertEquals("not-an-email", browser.find("#email").getDomProperty("value"), "what was typed should be kept");
        assertEquals("yes", browser.find("#surname").getDomAttribute("data-untouched"), "other fields should not be touched");
        assertEquals("Jo", browser.find("#first-name").getDomProperty("value"), "other fields should keep their values");
        assertEquals(SECRET, browser.find("#password").getDomProperty("value"), "other fields should keep their values");

        assertEquals(List.of("_csrf", "email"), parametersOfLastRequest(browser),
                "hx-params should limit the request to the field and the token, so no password is sent");
        verify(identityClient, description("identity should check the email")).validateField(AccountField.EMAIL, "not-an-email");
        verify(identityClient, timeout(CHECK_TIMEOUT_MILLIS).description("First name was left, so it should be checked too")).validateField(AccountField.FIRST_NAME, "Jo");
        verify(identityClient, timeout(CHECK_TIMEOUT_MILLIS).description("Password was left, so it should be checked too")).validateField(AccountField.PASSWORD, SECRET);
    }

    @Test
    void leavingOtherTitleWithAnotherTitleChosenShowsNoError(Browser browser) {
        browser.open("/create-account");
        recordRequests(browser);
        new Select(browser.find("#title")).selectByVisibleText("Mr");
        browser.script("document.querySelector('#title-other').dataset.notSwappedYet = 'yes'");

        browser.find("#title-other").sendKeys(TOO_LONG_FOR_OTHER_TITLE);
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the group to be swapped", () -> browser.find("#title-other").getDomAttribute("data-not-swapped-yet") == null);

        assertTrue(browser.driver().findElements(By.id("title-other-error")).isEmpty(), "no error unless Title is Other");
        assertEquals(List.of("_csrf", "title", "titleOther"), parametersOfLastRequest(browser),
                "hx-params should send Title, which the check depends on, and no other value");
        assertEquals(TOO_LONG_FOR_OTHER_TITLE, browser.find("#title-other").getDomProperty("value"), "what was typed should be kept");
        verify(identityClient, never().description("Other title shouldn't be checked unless Title is Other"))
                .validateField(eq(AccountField.TITLE_OTHER), anyString());
    }

    @Test
    void leavingOtherTitleWithOtherChosenChecksIt(Browser browser) {
        when(identityClient.validateField(AccountField.TITLE_OTHER, TOO_LONG_FOR_OTHER_TITLE)).thenReturn(new FieldValidationResult.Invalid("TITLE_OTHER_TOO_LONG"));
        browser.open("/create-account");
        new Select(browser.find("#title")).selectByVisibleText("Other");

        browser.find("#title-other").sendKeys(TOO_LONG_FOR_OTHER_TITLE);
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the Other title error to be swapped in", () -> !browser.driver().findElements(By.id("title-other-error")).isEmpty());

        assertTrue(browser.find("#title-other-error").getDomProperty("textContent").contains("Title must be 20 characters or fewer"), "the message should be shown");
        verify(identityClient, description("identity should check Other title")).validateField(AccountField.TITLE_OTHER, TOO_LONG_FOR_OTHER_TITLE);
    }

    @Test
    void confirmPasswordRequestHasBothPasswordsAndTheToken(Browser browser) {
        when(identityClient.validateField(any(), anyString(), anyString())).thenReturn(FieldValidationResult.valid());
        when(identityClient.validateField(any(), anyString())).thenReturn(FieldValidationResult.valid());
        browser.open("/create-account");
        recordRequests(browser);
        browser.find("#first-name").sendKeys("Jo");
        browser.find("#password").sendKeys("typed");
        browser.find("#confirm-password").sendKeys("typed");

        browser.sendKeys(Keys.TAB);
        waitForRequests(browser, 3);

        assertEquals(List.of("_csrf", "confirmPassword", "password"), parametersOfLastRequest(browser),
                "hx-params should limit the request to the two passwords and the token");
        verify(identityClient, description("identity should compare the two")).validateField(AccountField.CONFIRM_PASSWORD, "typed", "typed");
        verify(identityClient, description("First name was left, so it should be checked too")).validateField(AccountField.FIRST_NAME, "Jo");
        verify(identityClient, description("Password was left, so it should be checked too")).validateField(AccountField.PASSWORD, "typed");
    }

    @Test
    void focusStaysInASwappedInputWithTheSameId(Browser browser) {
        when(identityClient.validateField(AccountField.SURNAME, "x")).thenReturn(new FieldValidationResult.Invalid("SURNAME_TOO_LONG"));
        browser.open("/create-account");
        browser.script("""
                document.body.addEventListener('htmx:afterSwap', () => document.body.dataset.swapped = 'yes');
                document.body.addEventListener('htmx:beforeRequest', () => document.querySelector('#surname').focus());
                """);
        browser.find("#surname").sendKeys("x");

        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the group to be swapped", () -> "yes".equals(browser.find("body").getDomAttribute("data-swapped")));

        assertEquals("surname", browser.focused().getDomAttribute("id"),
                "someone who went back into the field before the response arrived should stay there");
        verify(identityClient, description("identity should check the surname")).validateField(AccountField.SURNAME, "x");
    }

    @Test
    void movingPastAnEmptyFieldShowsNoError(Browser browser) {
        browser.open("/create-account");
        recordRequests(browser);

        browser.find("#first-name").click();
        browser.sendKeys(Keys.TAB);
        waitForRequests(browser, 1);

        assertFalse(browser.find("#field-first-name").getDomAttribute("class").contains("form-group--error"), "no error until submit");
        assertTrue(browser.driver().findElements(By.id("first-name-error")).isEmpty(), "no message until submit");
        verify(identityClient, never().description("empty fields aren't sent to identity")).validateField(any(), anyString());
    }

    @Test
    void passwordErrorIsSwappedIntoTheSlotAndWhatWasTypedStays(Browser browser) {
        when(identityClient.validateField(AccountField.PASSWORD, "short")).thenReturn(new FieldValidationResult.Invalid("PASSWORD_TOO_SHORT"));
        browser.open("/create-account");
        browser.script("document.querySelector('#password').dataset.sameElement = 'yes'");

        browser.find("#password").sendKeys("short");
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the password error to be swapped in", () -> !browser.driver().findElements(By.id("password-error")).isEmpty());

        assertEquals("yes", browser.find("#password").getDomAttribute("data-same-element"), "the input should never be re-rendered");
        assertEquals("short", browser.find("#password").getDomProperty("value"), "what was typed should stay in the field");
        assertTrue(browser.find("#field-password").getDomAttribute("class").contains("form-group--error"), "the script should add the error class");
        assertEquals("true", browser.find("#password").getDomAttribute("aria-invalid"), "the script should set aria-invalid");
        verify(identityClient, description("identity should check the password")).validateField(AccountField.PASSWORD, "short");
    }

    @Test
    void confirmPasswordSendsThePasswordToo(Browser browser) {
        when(identityClient.validateField(AccountField.CONFIRM_PASSWORD, "other", "typed")).thenReturn(new FieldValidationResult.Invalid("PASSWORDS_DO_NOT_MATCH"));
        when(identityClient.validateField(AccountField.PASSWORD, "typed")).thenReturn(FieldValidationResult.valid());
        browser.open("/create-account");
        browser.find("#password").sendKeys("typed");
        browser.find("#confirm-password").sendKeys("other");

        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the mismatch error to be swapped in", () -> !browser.driver().findElements(By.id("confirm-password-error")).isEmpty());

        assertTrue(browser.find("#confirm-password-error").getDomProperty("textContent").contains("Passwords must match"), "the message should be shown");
        verify(identityClient, description("identity should compare with the password sent")).validateField(AccountField.CONFIRM_PASSWORD, "other", "typed");
        verify(identityClient, description("the blur on Password should have been checked as well")).validateField(AccountField.PASSWORD, "typed");
    }

    @Test
    void identityUnavailableChangesNothing(Browser browser) {
        when(identityClient.validateField(AccountField.EMAIL, "jo@example.com")).thenReturn(FieldValidationResult.unavailable());
        browser.open("/create-account");
        recordRequests(browser);
        browser.script("document.querySelector('#email').dataset.sameElement = 'yes'");

        browser.find("#email").sendKeys("jo@example.com");
        browser.sendKeys(Keys.TAB);
        waitForRequests(browser, 1);

        assertEquals("yes", browser.find("#email").getDomAttribute("data-same-element"), "a 204 should leave the page unchanged");
        assertNull(browser.find("#email").getDomAttribute("aria-invalid"), "a 204 should show no error");
        verify(identityClient, description("identity should be tried")).validateField(AccountField.EMAIL, "jo@example.com");
    }

    @Test
    void submittingWithAPasswordShownSignsUpAndLandsOnPropertiesWithTheBanner(Browser browser) {
        when(identityClient.validateField(any(), anyString())).thenReturn(FieldValidationResult.valid());
        when(identityClient.validateField(any(), anyString(), anyString())).thenReturn(FieldValidationResult.valid());
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.Created("account-1", "VISITOR"));
        browser.open("/create-account");
        new Select(browser.find("#title")).selectByVisibleText("Mr");
        browser.find("#first-name").sendKeys("Jo");
        browser.find("#surname").sendKeys("Bloggs");
        browser.find("#email").sendKeys("jo@example.com");
        browser.find("#phone").sendKeys("07700 900982");
        browser.find("#password").sendKeys(SECRET);
        browser.find("#confirm-password").sendKeys(SECRET);
        browser.find(".password-field__toggle[aria-controls=password]").click();

        browser.find("#create-account-form button[type=submit]").click();
        browser.waitUntil("the search page to load", () -> browser.currentUrl().endsWith("/properties"));

        assertEquals("Properties to let - Sunrise Letting", browser.driver().getTitle(), "sign-up should end on the search page");
        assertEquals("Your account has been created", browser.find(".notification-banner--success h2").getText(), "the banner should be shown");
        assertEquals(1, browser.driver().findElements(By.cssSelector(".notification-banner")).size(), "one banner");
        verify(identityClient, description("identity should create the account with what was typed")).createVisitor(
                new CreateVisitorRequest("Mr", "", "Jo", "Bloggs", "jo@example.com", "07700 900982", SECRET, SECRET));
        verify(identityClient, description("First name should be checked when it is left")).validateField(AccountField.FIRST_NAME, "Jo");
        verify(identityClient, description("Surname should be checked when it is left")).validateField(AccountField.SURNAME, "Bloggs");
        verify(identityClient, description("Email should be checked when it is left")).validateField(AccountField.EMAIL, "jo@example.com");
        verify(identityClient, description("Phone should be checked when it is left")).validateField(AccountField.PHONE, "07700 900982");
        verify(identityClient, description("Password should be checked when it is left")).validateField(AccountField.PASSWORD, SECRET);
        verify(identityClient, description("Confirm password should be checked when it is left")).validateField(AccountField.CONFIRM_PASSWORD, SECRET, SECRET);

        browser.driver().navigate().refresh();

        assertTrue(browser.driver().findElements(By.cssSelector(".notification-banner")).isEmpty(), "the banner should be shown once");
    }

    @Test
    void errorSummaryTakesFocusAfterAFailedSubmit(Browser browser) {
        when(identityClient.createVisitor(any())).thenReturn(new CreateVisitorResult.ValidationFailed(Map.of("email", "EMAIL_REQUIRED")));
        browser.open("/create-account");

        browser.find("#create-account-form button[type=submit]").click();

        // The browser applies autofocus at its next rendering step, a moment after the element exists, so wait for the focus itself.
        browser.waitUntil("focus to go to the error summary",
                () -> "error-summary".equals(browser.script("return document.activeElement.className")));

        verify(identityClient, description("identity should check the form as submitted, with every field empty"))
                .createVisitor(new CreateVisitorRequest("", "", "", "", "", "", "", ""));
    }
}
