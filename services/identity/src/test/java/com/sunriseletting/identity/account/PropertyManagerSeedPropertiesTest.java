package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class PropertyManagerSeedPropertiesTest {

    private static final PropertyManagerSeedProperties.SeededAccount FIRST =
            new PropertyManagerSeedProperties.SeededAccount("first@example.com", "first-temporary-pw");
    private static final PropertyManagerSeedProperties.SeededAccount SECOND =
            new PropertyManagerSeedProperties.SeededAccount("second@example.com", "second-temporary-pw");

    /** Binds as Spring Boot does at start-up, from a source treated as the real system environment. */
    private static PropertyManagerSeedProperties bindFromEnvironmentVariables(Map<String, Object> variables) {
        SystemEnvironmentPropertySource environment = new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables);
        return new Binder(ConfigurationPropertySources.from(environment))
                .bindOrCreate("identity", PropertyManagerSeedProperties.class);
    }

    @Test
    void indexedEnvironmentVariablesBindToTheList() {
        PropertyManagerSeedProperties properties = bindFromEnvironmentVariables(Map.of(
                "IDENTITY_PROPERTY_MANAGERS_0_EMAIL", "first@example.com",
                "IDENTITY_PROPERTY_MANAGERS_0_TEMPORARY_PASSWORD", "first-temporary-pw",
                "IDENTITY_PROPERTY_MANAGERS_1_EMAIL", "second@example.com",
                "IDENTITY_PROPERTY_MANAGERS_1_TEMPORARY_PASSWORD", "second-temporary-pw"));

        assertEquals(List.of(FIRST, SECOND), properties.propertyManagers(),
                "each indexed pair of variables should give one account, in order");
    }

    @Test
    void noVariablesGiveAnEmptyList() {
        PropertyManagerSeedProperties properties = bindFromEnvironmentVariables(Map.of());

        assertEquals(List.of(), properties.propertyManagers(), "with nothing configured there should be nothing to seed");
    }

    @Test
    void temporaryPasswordIsHiddenFromToString() {
        PropertyManagerSeedProperties properties = new PropertyManagerSeedProperties(List.of(FIRST));

        assertThat(properties.toString()).as("the temporary password should never be able to reach a log")
                .doesNotContain("first-temporary-pw");
        assertThat(properties.toString()).as("the email should still be there for debugging").contains("first@example.com");
    }
}
