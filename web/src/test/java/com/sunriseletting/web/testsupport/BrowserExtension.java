package com.sunriseletting.web.testsupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;

/**
 * Starts one Chrome container for the whole test run, makes the app's random port reachable from it,
 * and hands each test a {@link Browser} with no cookies, on a blank page.
 */
class BrowserExtension implements BeforeEachCallback, ParameterResolver {

    /** Keep the Grid version in step with the Selenium version Spring Boot manages. */
    private static final String CHROME_IMAGE = "selenium/standalone-chrome:4.43.0-20260404";

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);

    /** The container and driver last for the run: JUnit closes them when the root store closes. */
    private record Session(BrowserWebDriverContainer container, RemoteWebDriver driver) implements AutoCloseable {

        static Session start() {
            BrowserWebDriverContainer container = new BrowserWebDriverContainer(CHROME_IMAGE)
                    .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null)
                    .withAccessToHost(true);
            container.start();
            RemoteWebDriver driver = new RemoteWebDriver(container.getSeleniumAddress(), new ChromeOptions());
            return new Session(container, driver);
        }

        @Override
        public void close() {
            driver.quit();
            container.stop();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        browser(context).reset();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Browser.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return browser(context);
    }

    private Browser browser(ExtensionContext context) {
        Session session = context.getRoot().getStore(NAMESPACE)
                .computeIfAbsent(Session.class, key -> Session.start(), Session.class);
        int port = SpringExtension.getApplicationContext(context).getEnvironment()
                .getRequiredProperty("local.server.port", Integer.class);
        Testcontainers.exposeHostPorts(port);
        return new Browser(session.driver(), "http://host.testcontainers.internal:" + port);
    }
}
