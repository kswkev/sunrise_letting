package com.sunriseletting.web.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@BrowserTest
@Import(BrowserSetupIT.FixtureController.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrowserSetupIT {

    @Controller
    @TestComponent
    static class FixtureController {

        @GetMapping("/fixtures/browser-setup")
        String page() {
            return "fixtures/browser-setup";
        }

        @GetMapping("/fixtures/browser-setup/server-error")
        ResponseEntity<String> serverError() {
            return ResponseEntity.internalServerError().body("<p id=\"swapped-error\">Swapped error</p>");
        }

        @GetMapping("/fixtures/browser-setup/no-content")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }
    }

    @Test
    void homePageLoadsWithHtmx(Browser browser) {
        browser.open("/");

        assertEquals("Home - Sunrise Letting", browser.driver().getTitle(), "the home page should load in the browser");
        assertEquals("object", browser.script("return typeof htmx"), "HTMX should have loaded from the WebJar");
    }

    @Test
    void fixturePageFromSrcTestRunsItsOwnDeferredScript(Browser browser) {
        browser.open("/fixtures/browser-setup");

        browser.waitUntil("the fixture script to run",
                () -> "yes".equals(browser.find("#fixture-target").getDomAttribute("data-script-ran")));
        assertEquals("Fixture - Sunrise Letting", browser.driver().getTitle(), "the fixture should use the real layout");
    }

    @Test
    void waitHelperFailsWithAClearMessage(Browser browser) {
        TimeoutException failure = assertThrows(TimeoutException.class,
                () -> browser.waitUntil("something that never happens", Duration.ofMillis(200), () -> false),
                "the wait should give up");

        assertTrue(failure.getMessage().contains("something that never happens"),
                "the failure should say what it was waiting for: " + failure.getMessage());
    }

    @Test
    void waitHelperTreatsAStaleElementAsNotYet(Browser browser) {
        AtomicInteger calls = new AtomicInteger();

        browser.waitUntil("an element swapped out while it was read", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new StaleElementReferenceException("swapped out");
            }
            return true;
        });

        assertEquals(2, calls.get(), "the wait should try again after a stale element, then stop once the condition is true");
    }

    @Test
    void waitHelperStillFailsWithAClearMessageWhenTheElementStaysStale(Browser browser) {
        TimeoutException failure = assertThrows(TimeoutException.class,
                () -> browser.waitUntil("an element that stays stale", Duration.ofMillis(200), () -> {
                    throw new StaleElementReferenceException("swapped out");
                }),
                "the wait should give up");

        assertTrue(failure.getMessage().contains("an element that stays stale"),
                "the failure should say what it was waiting for: " + failure.getMessage());
    }

    @Test
    void browserIsChrome(Browser browser) {
        assertEquals("chrome", browser.driver().getCapabilities().getBrowserName(), "browser tests run in Chrome only");
    }

    @Test
    void htmxSwapsErrorResponses(Browser browser) {
        browser.open("/fixtures/browser-setup");

        browser.find("#load-error").click();

        browser.waitUntil("the 500 response to be swapped in", () -> !browser.driver().findElements(By.id("swapped-error")).isEmpty());
    }

    @Test
    void htmxDoesNotSwapNoContentResponses(Browser browser) {
        browser.open("/fixtures/browser-setup");
        browser.script("document.body.addEventListener('htmx:afterRequest', () => document.body.dataset.requestDone = 'yes')");

        browser.find("#load-nothing").click();

        browser.waitUntil("the 204 response to arrive", () -> "yes".equals(browser.find("body").getDomAttribute("data-request-done")));
        assertEquals("Unchanged", browser.find("#nothing-target").getText(), "a 204 should leave the page unchanged");
    }

    @Test
    @Order(1)
    void cookieSetInOneTest(Browser browser) {
        browser.open("/");

        browser.driver().manage().addCookie(new Cookie("left-behind", "yes"));

        assertEquals("yes", browser.driver().manage().getCookieNamed("left-behind").getValue(), "the cookie should be set");
    }

    @Test
    @Order(2)
    void isGoneInTheNext(Browser browser) {
        assertEquals("about:blank", browser.currentUrl(), "each test should start on a blank page");

        browser.open("/");

        assertNull(browser.driver().manage().getCookieNamed("left-behind"), "cookies shouldn't survive between tests");
    }
}
