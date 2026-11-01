package com.sunriseletting.web.home;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void homePageIsRenderedAsHtml() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("home page should load").hasStatusOk();
        assertThat(result).as("home page should be HTML").hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
        assertThat(result).as("home page should render the home view").hasViewName("home");
    }

    @Test
    void homePageIdentifiesItselfAsTheSunriseLettingHomePage() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("home page should load").hasStatusOk();
        assertThat(result).bodyText().as("home page title should follow '{Page} - Sunrise Letting'")
                .contains("<title>Home - Sunrise Letting</title>");
        assertThat(result).bodyText().as("home page should have a heading naming Sunrise Letting")
                .contains("<h1>Sunrise Letting</h1>");
    }
}
