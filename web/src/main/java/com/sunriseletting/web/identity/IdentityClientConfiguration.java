package com.sunriseletting.web.identity;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityClientProperties.class)
class IdentityClientConfiguration {

    @Bean
    IdentityClient identityClient(IdentityClientProperties properties, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new HttpIdentityClient(builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build());
    }
}
