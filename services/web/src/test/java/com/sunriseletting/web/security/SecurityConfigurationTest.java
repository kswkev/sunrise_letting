package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.sunriseletting.web.home.HomeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(HomeController.class)
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void pagesArePublic() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("a page should load with no one signed in").hasStatusOk();
    }

    @Test
    void postWithoutACsrfTokenIsRejected() {
        MvcTestResult result = mvc.post().uri("/").exchange();

        assertThat(result).as("a POST with no CSRF token should be rejected").hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWithAnInvalidCsrfTokenIsRejected() {
        MvcTestResult result = mvc.post().uri("/").with(csrf().useInvalidToken()).exchange();

        assertThat(result).as("a POST with a wrong CSRF token should be rejected").hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWithACsrfTokenGetsPastTheCheck() {
        MvcTestResult result = mvc.post().uri("/").with(csrf()).exchange();

        assertThat(result).as("the home page has no POST, so a good token should reach Spring MVC")
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void thereIsNoDefaultLoginPage() {
        MvcTestResult result = mvc.get().uri("/login").exchange();

        assertThat(result).as("Spring Security's default login page should be off").hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void thereIsNoHttpBasic() {
        MvcTestResult result = mvc.get().uri("/").header("Authorization", "Basic dXNlcjpwYXNz").exchange();

        assertThat(result).as("HTTP Basic credentials should be ignored").hasStatusOk();
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).as("there should be no Basic challenge").isNull();
    }

    @Test
    void thereIsNoDefaultLogoutEndpoint() {
        MvcTestResult result = mvc.post().uri("/logout").with(csrf()).exchange();

        assertThat(result).as("sign out comes with #2, so Spring Security's default should be off").hasStatus(HttpStatus.NOT_FOUND);
    }
}
