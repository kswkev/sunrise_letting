package com.sunriseletting.web.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sunriseletting.web.testsupport.Browser;
import com.sunriseletting.web.testsupport.BrowserTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Real-browser tests of static/js/session-timeout.js, on a fixture page with small time-outs and a keep-alive whose answer the test sets. */
@BrowserTest
@Import(SessionTimeoutIT.FixtureController.class)
class SessionTimeoutIT {

    @Controller
    @TestComponent
    static class FixtureController {

        final AtomicInteger keepAliveStatus = new AtomicInteger(204);

        final List<String> keepAliveTokens = new CopyOnWriteArrayList<>();

        @GetMapping("/fixtures/session-timeout")
        String page(@RequestParam(defaultValue = "3") int timeout, @RequestParam(defaultValue = "2") int warning,
                @RequestParam(defaultValue = "/fixtures/session-timeout/keep-alive") String keepAlive, Model model) {
            model.addAttribute("timeout", timeout);
            model.addAttribute("warning", warning);
            model.addAttribute("keepAliveUrl", keepAlive);
            return "fixtures/session-timeout";
        }

        @PostMapping("/fixtures/session-timeout/keep-alive")
        ResponseEntity<Void> keepAlive(@RequestParam("_csrf") String token) {
            keepAliveTokens.add(token);
            return ResponseEntity.status(keepAliveStatus.get()).build();
        }

        @GetMapping(value = "/fixtures/session-timeout/fragment", produces = "text/html")
        @ResponseBody
        String fragment() {
            return "<p id=\"loaded\">Loaded</p>";
        }

        @PostMapping(value = "/fixtures/session-timeout/signed-out", produces = "text/html")
        @ResponseBody
        String signedOut() {
            return "<h1 id=\"signed-out\">Signed out</h1>";
        }
    }

    private static final String PAGE = "/fixtures/session-timeout";

    private static final String TIMED_OUT = "/sign-in?timedOut=true&returnTo=%2Ffixtures%2Fsession-timeout%3Fa%3D1";

    @Autowired
    private FixtureController fixture;

    @BeforeEach
    void reset() {
        fixture.keepAliveStatus.set(204);
        fixture.keepAliveTokens.clear();
    }

    @Test
    void theDialogIsClosedOnLoadAndOpensAsAModalAfterTimeoutMinusWarning(Browser browser) {
        long started = System.nanoTime();
        browser.open(PAGE);
        assertFalse(isOpen(browser), "the dialog should be closed on load");

        waitForDialog(browser);

        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertTrue(elapsed >= 900, "it should open after timeout - warning, but took " + elapsed + "ms");
        assertTrue(browser.<Boolean>script("return document.querySelector('#timeout-dialog').matches(':modal')"), "it should be modal");
        assertEquals(stayButton(browser), browser.focused(), "focus should move to Stay signed in");
    }

    @Test
    void theOpenDialogIsNamedAndDescribedAndHasNoCountdown(Browser browser) {
        browser.open(PAGE);
        waitForDialog(browser);

        WebElement dialog = browser.find("#timeout-dialog");
        assertEquals("alertdialog", dialog.getDomAttribute("role"), "the role");
        assertEquals("You're about to be signed out", browser.find("#" + dialog.getDomAttribute("aria-labelledby")).getText(), "the name");
        String text = browser.find("#" + dialog.getDomAttribute("aria-describedby")).getText();
        assertEquals("We will sign you out in 2 minutes unless you choose to stay signed in.", text, "the description");

        long opened = System.nanoTime();
        browser.waitUntil("a second to pass with the dialog open", () -> Duration.ofNanos(System.nanoTime() - opened).toMillis() >= 1000);
        assertEquals(text, browser.find("#timeout-dialog-text").getText(), "the text doesn't count down");
    }

    @Test
    void staySignedInPostsTheTokenClosesTheDialogAndKeepsWhatWasTyped(Browser browser) {
        browser.open(PAGE);
        browser.find("#typed").sendKeys("something typed");
        waitForDialog(browser);
        String token = browser.find("#timeout-dialog form input[name=_csrf]").getDomProperty("value");
        assertNotNull(token, "the sign-out form should carry a token");

        stayButton(browser).click();

        browser.waitUntil("the dialog to close", () -> !isOpen(browser));
        assertEquals(List.of(token), fixture.keepAliveTokens, "keep-alive should post the sign-out form's token");
        assertEquals("something typed", browser.find("#typed").getDomProperty("value"), "what was typed stays");
        assertTrue(browser.currentUrl().endsWith(PAGE), "the page isn't reloaded or left");
        browser.waitUntil("focus to return", () -> browser.find("#typed").equals(browser.focused()));
    }

    @Test
    void afterStayingTheTimerRestartsAndTheDialogOpensAgain(Browser browser) {
        browser.open(PAGE);
        waitForDialog(browser);

        stayButton(browser).click();
        browser.waitUntil("the dialog to close", () -> !isOpen(browser));

        waitForDialog(browser);
        assertEquals(1, fixture.keepAliveTokens.size(), "a second warning means the timer started again");
    }

    @Test
    void whenTheFocusedElementHasGoneFocusGoesToMain(Browser browser) {
        browser.open(PAGE);
        browser.find("#typed").click();
        waitForDialog(browser);
        browser.script("document.getElementById('typed').remove()");

        stayButton(browser).click();

        browser.waitUntil("the dialog to close", () -> !isOpen(browser));
        browser.waitUntil("focus to go to main", () -> browser.find("main").equals(browser.focused()));
    }

    @Test
    void escapeWithNoEarlierInteractionKeepsAliveAndDoesNotJustClose(Browser browser) {
        browser.open(PAGE);
        browser.script("document.getElementById('typed').focus()");
        waitForDialog(browser);

        browser.sendKeys(Keys.ESCAPE);

        browser.waitUntil("keep-alive to be posted", () -> fixture.keepAliveTokens.size() == 1);
        browser.waitUntil("the dialog to close", () -> !isOpen(browser));
        browser.waitUntil("focus to return", () -> browser.find("#typed").equals(browser.focused()));
    }

    @Test
    void escapeAfterEarlierInteractionKeepsAliveToo(Browser browser) {
        browser.open(PAGE);
        browser.find("#typed").click();
        browser.find("#typed").sendKeys("x");
        waitForDialog(browser);

        browser.sendKeys(Keys.ESCAPE);

        browser.waitUntil("keep-alive to be posted", () -> fixture.keepAliveTokens.size() == 1);
        browser.waitUntil("the dialog to close", () -> !isOpen(browser));
        assertEquals(1, fixture.keepAliveTokens.size(), "one press is one keep-alive");
    }

    @Test
    void escapeThatFailsGoesToTheTimedOutAddress(Browser browser) {
        fixture.keepAliveStatus.set(401);
        browser.open(PAGE + "?a=1");
        waitForDialog(browser);

        browser.sendKeys(Keys.ESCAPE);

        assertLandsOnTimedOut(browser);
    }

    @Test
    void keepAlive401GoesToTheTimedOutAddressAndBackDoesNotReturn(Browser browser) {
        fixture.keepAliveStatus.set(401);
        browser.open(PAGE + "?a=1");
        waitForDialog(browser);

        stayButton(browser).click();

        assertLandsOnTimedOut(browser);
        assertEquals("You were signed out because you were inactive", browser.find(".notification-banner--info h2").getText(), "the page says why");
        assertBackDoesNotReturn(browser);
    }

    @Test
    void keepAlive500GoesToTheTimedOutAddressAndBackDoesNotReturn(Browser browser) {
        fixture.keepAliveStatus.set(500);
        browser.open(PAGE + "?a=1");
        waitForDialog(browser);

        stayButton(browser).click();

        assertLandsOnTimedOut(browser);
        assertBackDoesNotReturn(browser);
    }

    @Test
    void keepAliveNetworkFailureGoesToTheTimedOutAddressAndBackDoesNotReturn(Browser browser) {
        browser.open(PAGE + "?keepAlive=http%3A%2F%2Flocalhost%3A1%2Funreachable");
        waitForDialog(browser);

        stayButton(browser).click();

        browser.waitUntil("the browser to go to the timed-out address", () -> browser.currentUrl().contains("/sign-in?timedOut=true&returnTo="));
        assertBackDoesNotReturn(browser);
    }

    @Test
    void noResponseGoesToTheTimedOutAddressAfterTimeoutPlusFiveSeconds(Browser browser) {
        long started = System.nanoTime();
        browser.open(PAGE + "?a=1");
        waitForDialog(browser);

        browser.waitUntil("the browser to go to the timed-out address", Duration.ofSeconds(15), () -> browser.currentUrl().endsWith(TIMED_OUT));

        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertTrue(elapsed >= 8000, "it should wait for timeout + 5 seconds, but took " + elapsed + "ms");
        assertBackDoesNotReturn(browser);
    }

    @Test
    void aCompletedHtmxRequestRestartsTheTimer(Browser browser) {
        browser.open(PAGE + "?timeout=6&warning=2");
        long started = System.nanoTime();
        browser.waitUntil("two seconds to pass", () -> elapsedMillis(started) >= 2000);

        browser.find("#htmx-button").click();
        browser.waitUntil("the fragment to load", () -> !browser.driver().findElements(org.openqa.selenium.By.id("loaded")).isEmpty());
        long loaded = System.nanoTime();

        browser.waitUntil("the original warning time to pass", () -> elapsedMillis(started) >= 5000);
        assertFalse(isOpen(browser), "the warning should have moved back, so it isn't showing at the original time");
        waitForDialog(browser);
        assertTrue(elapsedMillis(loaded) >= 3500, "it should open about 4 seconds after the request, but took " + elapsedMillis(loaded) + "ms");
    }

    @Test
    void signOutIsANormalFormPost(Browser browser) {
        browser.open(PAGE);
        waitForDialog(browser);

        browser.find("#timeout-dialog form button").click();

        browser.waitUntil("the sign-out page to load", () -> browser.currentUrl().endsWith("/fixtures/session-timeout/signed-out"));
        assertEquals(0, fixture.keepAliveTokens.size(), "the script doesn't get involved");
    }

    private long elapsedMillis(long since) {
        return Duration.ofNanos(System.nanoTime() - since).toMillis();
    }

    private boolean isOpen(Browser browser) {
        return browser.find("#timeout-dialog").getDomAttribute("open") != null;
    }

    private void waitForDialog(Browser browser) {
        browser.waitUntil("the dialog to open", () -> isOpen(browser));
    }

    private WebElement stayButton(Browser browser) {
        return browser.find(".timeout-dialog__stay");
    }

    private void assertLandsOnTimedOut(Browser browser) {
        browser.waitUntil("the browser to go to the timed-out address", () -> browser.currentUrl().endsWith(TIMED_OUT));
    }

    private void assertBackDoesNotReturn(Browser browser) {
        browser.driver().navigate().back();
        assertFalse(browser.currentUrl().contains(PAGE), "Back shouldn't return to the old page, but is on " + browser.currentUrl());
    }
}
