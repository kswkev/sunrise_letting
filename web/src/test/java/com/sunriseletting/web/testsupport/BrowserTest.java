package com.sunriseletting.web.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sunriseletting.web.TestcontainersConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A real-browser test of our own JavaScript: the whole app on a random port, Redis from Testcontainers,
 * and Chrome in a container, reached through {@link Browser}, which test methods take as a parameter.
 * Name the class {@code *IT}, so Failsafe runs it in {@code mvnw verify} and not in {@code mvnw test}.
 * Replace domain service clients with {@code @MockitoBean}, so no domain service has to run.
 * Pages for a test go in a test-only controller (add it with {@code @Import}) and templates under
 * {@code src/test/resources/templates/fixtures/}.
 *
 * <p>The session cookie isn't {@code Secure} here: the browser reaches the app over plain HTTP on a
 * host name that isn't localhost, so it wouldn't send a {@code Secure} cookie back.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
@ExtendWith(BrowserExtension.class)
public @interface BrowserTest {
}
