package com.sunriseletting.web.forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.sunriseletting.web.testsupport.Browser;
import com.sunriseletting.web.testsupport.BrowserTest;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Real-browser tests of static/js/password-toggle.js, on fixture pages built from the password field component. */
@BrowserTest
@Import(PasswordToggleIT.FixtureController.class)
class PasswordToggleIT {

    @Controller
    @TestComponent
    static class FixtureController {

        @GetMapping("/fixtures/password-toggle")
        String withScript() {
            return "fixtures/password-toggle";
        }

        @GetMapping("/fixtures/password-toggle/no-script")
        String withoutScript() {
            return "fixtures/password-toggle-no-script";
        }

        /** Answers like an inline password check: an error message, or an empty body. */
        @PostMapping(value = "/fixtures/password-toggle/validate", produces = "text/html")
        @ResponseBody
        String validate(@RequestParam String secret) {
            return "bad".equals(secret) ? "<p class=\"error-message\" id=\"secret-error\">That is a bad one</p>" : "";
        }
    }

    private static final String PAGE = "/fixtures/password-toggle";

    @Test
    void scriptRevealsEveryToggle(Browser browser) {
        browser.open(PAGE);

        List<WebElement> toggles = browser.driver().findElements(By.cssSelector(".password-field__toggle"));

        assertEquals(2, toggles.size(), "the fixture has two password fields");
        assertTrue(toggles.get(0).isDisplayed(), "the first toggle should be revealed");
        assertTrue(toggles.get(1).isDisplayed(), "the second toggle should be revealed");
    }

    @Test
    void withoutTheScriptTheToggleStaysHiddenAndTheFieldIsMasked(Browser browser) {
        browser.open("/fixtures/password-toggle/no-script");

        assertFalse(toggle(browser, "secret").isDisplayed(), "without the script the toggle should stay hidden");
        assertEquals("password", browser.find("#secret").getDomAttribute("type"), "without the script the input stays masked");
    }

    @Test
    void showRevealsThePasswordAndHideMasksItAgain(Browser browser) {
        browser.open(PAGE);
        WebElement toggle = toggle(browser, "secret");

        toggle.click();

        assertEquals("text", browser.find("#secret").getDomProperty("type"), "Show should reveal the password");
        assertEquals("Hide", toggleTextBeforeSuffix(browser, toggle), "the button should offer to hide");
        assertEquals(" secret", suffix(browser, toggle), "the hidden suffix should be kept");
        assertEquals(toggle, browser.focused(), "focus should stay on the button");
        assertEquals("Your password is visible", status(browser, "secret"), "the status should announce it");

        toggle.click();

        assertEquals("password", browser.find("#secret").getDomProperty("type"), "Hide should mask the password");
        assertEquals("Show", toggleTextBeforeSuffix(browser, toggle), "the button should offer to show");
        assertEquals(" secret", suffix(browser, toggle), "the hidden suffix should be kept");
        assertEquals(toggle, browser.focused(), "focus should stay on the button");
        assertEquals("Your password is hidden", status(browser, "secret"), "the status should announce it");
    }

    @Test
    void togglingOneFieldLeavesTheOtherAlone(Browser browser) {
        browser.open(PAGE);

        toggle(browser, "secret").click();

        assertEquals("password", browser.find("#confirm-secret").getDomProperty("type"), "the other field should stay masked");
        assertEquals("", status(browser, "confirm-secret"), "the other field's status should stay empty");
    }

    @Test
    void typedTextIsUnchangedAfterToggling(Browser browser) {
        browser.open(PAGE);
        browser.find("#secret").sendKeys("Typed-Pa55word!");
        WebElement toggle = toggle(browser, "secret");

        toggle.click();
        assertEquals("Typed-Pa55word!", browser.find("#secret").getDomProperty("value"), "showing should keep the text");
        toggle.click();

        assertEquals("Typed-Pa55word!", browser.find("#secret").getDomProperty("value"), "hiding should keep the text");
    }

    @Test
    void submittingWithAPasswordShownMasksEveryPasswordInputFirst(Browser browser) {
        browser.open(PAGE);
        toggle(browser, "secret").click();
        toggle(browser, "confirm-secret").click();
        // The window hears the event last, so this sees what the script's listener left. It then stops the page navigating.
        browser.script("""
                window.addEventListener('submit', event => {
                    event.preventDefault();
                    window.typesAtSubmit = [...document.querySelectorAll('.password-field__input')].map(input => input.type);
                });
                """);

        browser.find("#fixture-submit").click();

        List<String> types = browser.script("return window.typesAtSubmit");
        assertEquals(List.of("password", "password"), types, "every password input should be masked at submit");
    }

    @Test
    void errorMessageSwappedIntoTheSlotAddsTheErrorStateAndAnEmptyOneRemovesIt(Browser browser) {
        browser.open(PAGE);
        WebElement input = browser.find("#secret");

        input.sendKeys("bad");
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the error message to be swapped in",
                () -> !browser.find("#secret-error-slot").getText().isEmpty());

        assertTrue(browser.find("#field-secret").getDomAttribute("class").contains("form-group--error"),
                "an error in the slot should add the error class");
        assertEquals("true", input.getDomAttribute("aria-invalid"), "an error in the slot should set aria-invalid");
        assertEquals("secret-error", input.getDomAttribute("aria-describedby"), "an error in the slot should be read with the input");
        assertEquals("bad", input.getDomProperty("value"), "what was typed should stay in the field");

        input.clear();
        input.sendKeys("good");
        browser.sendKeys(Keys.TAB);
        browser.waitUntil("the error message to be removed",
                () -> browser.find("#secret-error-slot").getText().isEmpty());

        assertFalse(browser.find("#field-secret").getDomAttribute("class").contains("form-group--error"),
                "an empty slot should remove the error class");
        assertNull(input.getDomAttribute("aria-invalid"), "an empty slot should remove aria-invalid");
        assertNull(input.getDomAttribute("aria-describedby"), "an empty slot should leave nothing for the input to reference");
        assertEquals("good", input.getDomProperty("value"), "what was typed should stay in the field");
    }

    private WebElement toggle(Browser browser, String inputId) {
        return browser.find(".password-field__toggle[aria-controls=" + inputId + "]");
    }

    private String toggleTextBeforeSuffix(Browser browser, WebElement toggle) {
        return browser.script("return arguments[0].firstChild.nodeValue", toggle);
    }

    private String suffix(Browser browser, WebElement toggle) {
        return browser.script("return arguments[0].querySelector('.visually-hidden').textContent", toggle);
    }

    private String status(Browser browser, String inputId) {
        return browser.script("return document.querySelector('#field-' + arguments[0] + ' .password-field__status').textContent", inputId);
    }
}
