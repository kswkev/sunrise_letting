package com.sunriseletting.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.sunriseletting.web.identity.AuthenticationResult;
import com.sunriseletting.web.identity.IdentityClient;
import com.sunriseletting.web.identity.RememberMeTokenResult;
import com.sunriseletting.web.identity.RememberMeVerifyResult;
import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.testsupport.Browser;
import com.sunriseletting.web.testsupport.BrowserTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The real pages in a real browser, which also keeps the cookies as a browser does. Identity is mocked. */
@BrowserTest
class SignInJourneyIT {

    private static final String SECRET = "Sup3r-Secret-Pa55!";

    @MockitoBean
    private IdentityClient identityClient;

    @BeforeEach
    void resetMock() {
        reset(identityClient);
    }

    @Test
    void signingInWithoutRememberMeShowsTheClosedDialogAndSigningOutGoesBackToSignedOut(Browser browser) {
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        browser.open("/sign-in?returnTo=/properties/123");
        assertTrue(browser.find(".password-field__toggle").isDisplayed(), "the password script should run on this page");

        signIn(browser, false);

        // The sign-in page's own address already ends in the return address, so only the whole address shows the browser has arrived.
        browser.waitUntil("the return address to load", () -> browser.currentUrl().equals(browser.baseUrl() + "/properties/123"));
        browser.open("/properties");
        assertNotNull(browser.driver().manage().getCookieNamed("signed-in"), "the marker should be set");
        assertNull(browser.driver().manage().getCookieNamed("remember-me"), "the box wasn't ticked");
        assertTrue(browser.find("header form.sign-out button").isDisplayed(), "the header offers Sign out");
        assertFalse(browser.find("#timeout-dialog").isDisplayed(), "the dialog is there, but closed");

        browser.find("header form.sign-out button").click();

        browser.waitUntil("the search page to load", () -> browser.currentUrl().endsWith("/properties"));
        browser.waitUntil("the signed-out header", () -> !browser.driver().findElements(By.cssSelector("header a[href='/sign-in']")).isEmpty());
        assertNull(browser.driver().manage().getCookieNamed("signed-in"), "the marker should be gone");
        assertNull(browser.driver().manage().getCookieNamed("SESSION"), "the session cookie should be gone");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    @Test
    void afterTheBrowserLosesItsSessionRememberMeSignsBackInWithNoDialog(Browser browser) {
        String series = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        String token = "T".repeat(43);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        when(identityClient.authenticate("jo@example.com", SECRET)).thenReturn(new AuthenticationResult.SignedIn("account-1", AccountRole.VISITOR, false));
        when(identityClient.issueRememberMeToken("account-1")).thenReturn(new RememberMeTokenResult.Issued(series, token, expiresAt));
        when(identityClient.verifyRememberMeToken(series, token)).thenReturn(
                new RememberMeVerifyResult.Verified("account-1", AccountRole.VISITOR, "N".repeat(43), expiresAt));
        browser.open("/sign-in");

        signIn(browser, true);

        browser.waitUntil("the search page to load", () -> browser.currentUrl().endsWith("/properties"));
        Cookie cookie = browser.driver().manage().getCookieNamed("remember-me");
        assertEquals(series + "." + token, cookie.getValue(), "the browser should hold the cookie");
        assertNotNull(cookie.getExpiry(), "the cookie should outlive the browser");
        assertTrue(browser.driver().findElements(By.id("timeout-dialog")).isEmpty(), "a remembered session shows no warning");

        browser.driver().manage().deleteCookieNamed("SESSION");
        browser.driver().manage().deleteCookieNamed("signed-in");
        browser.open("/properties");

        assertTrue(browser.find("header form.sign-out button").isDisplayed(), "the page and its files together exchange the cookie, and sign the Visitor in");
        assertEquals(series + "." + "N".repeat(43), browser.driver().manage().getCookieNamed("remember-me").getValue(), "the browser should hold the new token");
        verify(identityClient, description("the page's scripts and stylesheet must not spend the token as well")).verifyRememberMeToken(series, token);
        verify(identityClient, description("the token should be issued")).issueRememberMeToken("account-1");
        verify(identityClient, description("identity should be asked")).authenticate("jo@example.com", SECRET);
    }

    private void signIn(Browser browser, boolean rememberMe) {
        browser.find("#email").sendKeys("jo@example.com");
        browser.find("#password").sendKeys(SECRET);
        if (rememberMe) {
            browser.find("#remember-me").click();
        }
        browser.find("#sign-in-form button[type=submit]").click();
    }
}
