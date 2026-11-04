package com.sunriseletting.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.sunriseletting.web.home.HomeController;
import com.sunriseletting.web.manage.ManageController;
import com.sunriseletting.web.properties.PropertiesController;
import com.sunriseletting.web.testsupport.SignedInUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({ ManageController.class, PropertiesController.class, HomeController.class })
@Import(SecurityConfiguration.class)
class TemporaryPasswordFilterTest {

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest
    @ValueSource(strings = { "/manage", "/manage/", "/manage/other", "/manage/other/page?a=1", "/manage/change-password/", "/manage/change-password/other" })
    void aTemporaryPasswordIsSentFromEveryOtherManagerPageToChangePassword(String path) {
        MvcTestResult result = mvc.get().uri(path).with(authentication(SignedInUsers.propertyManager(true))).exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("to the change-password page").hasRedirectedUrl("/manage/change-password");
    }

    @Test
    void aPostIsSentToChangePasswordToo() {
        MvcTestResult result = mvc.post().uri("/manage/other").with(csrf()).with(authentication(SignedInUsers.propertyManager(true))).exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("every method is covered").hasRedirectedUrl("/manage/change-password");
    }

    @Test
    void changePasswordItselfDoesNotRedirectToItself() {
        MvcTestResult get = mvc.get().uri("/manage/change-password").with(authentication(SignedInUsers.propertyManager(true))).exchange();
        MvcTestResult post = mvc.post().uri("/manage/change-password").with(csrf()).with(authentication(SignedInUsers.propertyManager(true))).exchange();

        assertThat(get).as("no redirect loop: a 404 only because this test doesn't load the page's controller").hasStatus(HttpStatus.NOT_FOUND);
        assertThat(post).as("nor for its form: a 404 for the same reason").hasStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/properties" })
    void publicPagesOpenAsNormal(String path) {
        MvcTestResult result = mvc.get().uri(path).with(authentication(SignedInUsers.propertyManager(true))).exchange();

        assertThat(result).as("the rule is only for the Property Manager area").hasStatusOk();
    }

    @Test
    void aPermanentPasswordIsNotRedirected() {
        MvcTestResult result = mvc.get().uri("/manage").with(authentication(SignedInUsers.propertyManager(false))).exchange();

        assertThat(result).as("the home page should load").hasStatusOk();
    }

    @Test
    void aVisitorIsDeniedNotRedirected() {
        MvcTestResult result = mvc.get().uri("/manage").with(authentication(SignedInUsers.visitor())).exchange();

        assertThat(result).as("the access rule comes first").hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void signedOutGoesToSignInNotChangePassword() {
        MvcTestResult result = mvc.get().uri("/manage/change-password").exchange();

        assertThat(result).as("the access rule comes first").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage%2Fchange-password");
    }
}
