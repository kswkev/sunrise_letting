package com.sunriseletting.web.testsupport;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** What a browser test does with Chrome. Waits never use {@code Thread.sleep}. */
public class Browser {

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private final RemoteWebDriver driver;

    private final String baseUrl;

    Browser(RemoteWebDriver driver, String baseUrl) {
        this.driver = driver;
        this.baseUrl = baseUrl;
    }

    /** The underlying driver, for anything the helpers below don't cover. */
    public RemoteWebDriver driver() {
        return driver;
    }

    /** The address the browser reaches the app on. */
    public String baseUrl() {
        return baseUrl;
    }

    /** Opens a path on the app and waits for the deferred scripts to have run. */
    public void open(String path) {
        driver.get(baseUrl + path);
    }

    public WebElement find(String cssSelector) {
        return driver.findElement(By.cssSelector(cssSelector));
    }

    /** Waits for a condition, failing with the description if it never becomes true. */
    public void waitUntil(String description, BooleanSupplier condition) {
        waitUntil(description, DEFAULT_WAIT, condition);
    }

    public void waitUntil(String description, Duration timeout, BooleanSupplier condition) {
        new WebDriverWait(driver, timeout)
                .withMessage("Waiting for: " + description)
                .until((WebDriver ignored) -> condition.getAsBoolean());
    }

    /** Runs a script in the page and returns its result, for example {@code return typeof htmx}. */
    @SuppressWarnings("unchecked")
    public <T> T script(String script, Object... args) {
        return (T) ((JavascriptExecutor) driver).executeScript(script, args);
    }

    /** Dispatches a bubbling DOM event on an element. */
    public void dispatchEvent(WebElement element, String eventName) {
        script("arguments[0].dispatchEvent(new Event(arguments[1], { bubbles: true }))", element, eventName);
    }

    /** The element that has focus. */
    public WebElement focused() {
        return driver.switchTo().activeElement();
    }

    /** Sends keys such as {@code Keys.ESCAPE} or {@code Keys.TAB} to whatever has focus. */
    public void sendKeys(CharSequence... keys) {
        new Actions(driver).sendKeys(keys).perform();
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /** The browser's history length, for checks that {@code location.replace} added no entry. */
    public long historyLength() {
        return script("return history.length");
    }

    /** Back to a clean browser: no cookies, on a blank page. */
    void reset() {
        if (driver.getCurrentUrl().startsWith("http")) {
            driver.manage().deleteAllCookies();
        }
        driver.get("about:blank");
    }
}
