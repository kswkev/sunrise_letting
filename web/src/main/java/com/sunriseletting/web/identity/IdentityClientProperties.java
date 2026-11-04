package com.sunriseletting.web.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where identity is, and how long to wait for it. */
@ConfigurationProperties("identity")
public record IdentityClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
