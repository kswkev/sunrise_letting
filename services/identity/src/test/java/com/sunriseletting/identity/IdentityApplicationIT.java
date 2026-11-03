package com.sunriseletting.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Starts the whole application against PostgreSQL in Testcontainers, with no compose.yaml involved.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdentityApplicationIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Environment environment;

    @Test
    void flywayAppliesTheBaselineMigration() {
        List<String> applied = jdbc.sql("select version from flyway_schema_history where version = '1' and success")
                .query(String.class)
                .list();

        assertEquals(List.of("1"), applied, "Flyway should have applied the baseline migration");
    }

    @Test
    void theDataStoreIsPostgres() {
        String product = jdbc.sql("select version()").query(String.class).single();

        assertEquals("PostgreSQL", product.split(" ")[0], "tests should run against real PostgreSQL");
    }

    @Test
    void loggersThatCanPrintRequestBodiesAreKeptQuiet() {
        assertEquals("false", environment.getProperty("spring.mvc.log-request-details"),
                "Spring MVC should not log request details");
        for (String logger : List.of(
                "org.springframework.web.filter.CommonsRequestLoggingFilter",
                "org.springframework.web.servlet.mvc.method.annotation",
                "org.springframework.http.converter")) {
            assertFalse(LoggerFactory.getLogger(logger).isDebugEnabled(),
                    logger + " should not be able to log request bodies at debug level");
        }
    }
}
