package com.sunriseletting.web.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.testsupport.Html;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PropertiesController.class)
@Import(SecurityConfiguration.class)
class PropertiesControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void pageHasTheTitleAndHeadingAndNoBanner() {
        MvcTestResult result = mvc.get().uri("/properties").exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should name the page").isEqualTo("Properties to let - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("the heading should name the page").isEqualTo("Properties to let");
        assertThat(page.select(".notification-banner")).as("no banner without the flash").isEmpty();
        assertThat(page.select("main > *")).as("main holds only the heading until #4").hasSize(1);
    }

    @Test
    void headerMarksPropertiesToLetAsCurrent() {
        MvcTestResult result = mvc.get().uri("/properties").exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        assertThat(Html.page(result).select("header nav a[aria-current=page]").eachText())
                .as("only Properties to let should be current").containsExactly("Properties to let");
    }

    @Test
    void flashShowsTheSuccessBannerAboveTheHeading() {
        MvcTestResult result = mvc.get().uri("/properties").flashAttr(PropertiesController.ACCOUNT_CREATED, true).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        Element banner = page.selectFirst("main > .notification-banner--success[role=status][autofocus]");
        assertThat(banner).as("the success banner should be at the top of main").isNotNull();
        assertThat(banner.select("h2").text()).as("the banner should have the agreed copy").isEqualTo("Your account has been created");
        assertThat(banner.nextElementSibling().tagName()).as("the banner should sit directly above the heading").isEqualTo("h1");
        assertThat(banner.elementSiblingIndex()).as("the banner should come first in main").isZero();
    }

    @Test
    void secondRequestWithoutTheFlashHasNoBanner() {
        mvc.get().uri("/properties").flashAttr(PropertiesController.ACCOUNT_CREATED, true).exchange();

        MvcTestResult second = mvc.get().uri("/properties").exchange();

        assertThat(second).as("the page should load").hasStatusOk();
        assertThat(Html.page(second).select(".notification-banner")).as("the banner should be shown only once").isEmpty();
    }
}
