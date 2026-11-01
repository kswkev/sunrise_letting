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

    @Test
    void homePageIsBuiltFromTheSharedLayout() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("home page should load").hasStatusOk();
        assertThat(result).bodyText().as("layout should set the page language to British English")
                .contains("<html lang=\"en-GB\">");
        assertThat(result).bodyText().as("layout should set the viewport for mobile")
                .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        assertThat(result).bodyText().as("layout should start the body with a skip link to the main content")
                .containsPattern("<body>\\s*<a class=\"skip-link\" href=\"#main-content\">Skip to main content</a>");
        assertThat(result).bodyText().as("layout header should link the site name to the home page")
                .containsPattern("<header class=\"site-header\">\\s*<a href=\"/\">Sunrise Letting</a>\\s*</header>");
        assertThat(result).bodyText().as("layout should wrap the page content in the main landmark")
                .containsPattern("<main id=\"main-content\" tabindex=\"-1\">\\s*<h1>Sunrise Letting</h1>\\s*</main>");
        assertThat(result).bodyText().as("layout footer should show only the copyright line")
                .containsPattern("<footer class=\"site-footer\">\\s*<p>© Sunrise Letting</p>\\s*</footer>");
    }

    @Test
    void homePageLoadsHtmxFromTheWebJar() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("home page should load").hasStatusOk();
        assertThat(result).bodyText().as("layout should load HTMX deferred from the version-less WebJar path")
                .contains("<script src=\"/webjars/htmx.org/dist/htmx.min.js\" defer></script>");
    }
}
