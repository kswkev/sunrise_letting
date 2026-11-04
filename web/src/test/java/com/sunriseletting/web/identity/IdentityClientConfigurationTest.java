package com.sunriseletting.web.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class IdentityClientConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(IdentityClientConfiguration.class);

    @Test
    void applicationConfigurationPointsAtIdentityOnItsLocalPortWithTimeouts() {
        contexts.run(context -> {
            IdentityClientProperties properties = context.getBean(IdentityClientProperties.class);

            assertEquals("http://localhost:8081", properties.baseUrl(), "the local base URL should be identity's port");
            assertThat(properties.connectTimeout()).as("there should be a connect timeout").isPositive();
            assertThat(properties.readTimeout()).as("there should be a read timeout").isPositive();
        });
    }

    @Test
    void baseUrlAndTimeoutsCanBeConfigured() {
        contexts.withPropertyValues("identity.base-url=http://identity:8080", "identity.connect-timeout=1s", "identity.read-timeout=2s")
                .run(context -> {
                    IdentityClientProperties properties = context.getBean(IdentityClientProperties.class);

                    assertEquals("http://identity:8080", properties.baseUrl(), "the base URL should come from configuration");
                    assertEquals(Duration.ofSeconds(1), properties.connectTimeout(), "the connect timeout should come from configuration");
                    assertEquals(Duration.ofSeconds(2), properties.readTimeout(), "the read timeout should come from configuration");
                });
    }

    @Test
    void connectionRefusedIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        contexts.withPropertyValues("identity.base-url=http://localhost:" + closedPort).run(context -> {
            IdentityClient client = context.getBean(IdentityClient.class);

            assertEquals(FieldValidationResult.unavailable(), client.validateField(AccountField.EMAIL, "x"),
                    "a refused connection means the check can't be done");
        });
    }

    @Test
    void identityThatNeverAnswersIsUnavailableAfterTheReadTimeout() throws IOException {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            contexts.withPropertyValues("identity.base-url=http://localhost:" + silent.getLocalPort(), "identity.read-timeout=200ms")
                    .run(context -> {
                        IdentityClient client = context.getBean(IdentityClient.class);

                        FieldValidationResult result = assertTimeoutPreemptively(Duration.ofSeconds(10),
                                () -> client.validateField(AccountField.EMAIL, "x"),
                                "the call should give up at the read timeout");

                        assertEquals(FieldValidationResult.unavailable(), result, "a timeout means the check can't be done");
                    });
        }
    }
}
